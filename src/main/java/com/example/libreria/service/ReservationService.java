package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.dto.UserResponseDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% por día

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final UserService userService;

    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {
        log.info("Creando reserva para usuario: {}, libro: {}", requestDTO.getUserId(), requestDTO.getBookExternalId());

        // TODO: Implementar la creación de una reserva
        // Validar que el usuario existe
        
        UserResponseDTO user = userService.getUserById(requestDTO.getUserId());
        if (user == null) {
            throw new RuntimeException("Usuario no encontrado con ID: " + requestDTO.getUserId());
        }

        // Validar que el libro existe y está disponible
        
        // Crear la reserva
        
        // Reducir la cantidad disponible
        Book book = bookRepository.findByExternalId(requestDTO.getBookExternalId())
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + requestDTO.getBookExternalId()));

        if (book.getAvailableQuantity() <= 0) {
            throw new RuntimeException("Libro no disponible. Stock actual: " + book.getAvailableQuantity());
        }

        // Verificar si el usuario ya tiene una reserva activa para este libro
        boolean hasActiveReservation = reservationRepository.existsByUserIdAndBookExternalIdAndActualReturnDateIsNull(
                requestDTO.getUserId(), requestDTO.getBookExternalId());
        if (hasActiveReservation) {
            throw new RuntimeException("El usuario ya tiene una reserva activa para este libro");
        }

        Reservation reservation = new Reservation();
        reservation.setUser(userService.getUserEntityById(requestDTO.getUserId()));
        reservation.setBook(book);
        reservation.setRentalDays(requestDTO.getRentalDays());
        reservation.setStartDate(requestDTO.getStartDate());
        reservation.setExpectedReturnDate(requestDTO.getStartDate().plusDays(requestDTO.getRentalDays()));
        reservation.setDailyRate(book.getPrice());
        reservation.setTotalFee(calculateTotalFee(book.getPrice(), requestDTO.getRentalDays()));
        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        reservation.setCreatedAt(LocalDateTime.now());

        Reservation savedReservation = reservationRepository.save(reservation);

        book.setAvailableQuantity(book.getAvailableQuantity() - 1);
        bookRepository.save(book);

        log.info("Reserva creada exitosamente con ID: {}", savedReservation.getId());
        return convertToDTO(savedReservation);
    }

    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {
        log.info("Procesando devolución para reserva ID: {}", reservationId);

        // TODO: Implementar la devolución de un libro
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + reservationId));

        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La reserva ya fue devuelta");
        }

        LocalDate returnDate = returnRequest.getReturnDate();
        reservation.setActualReturnDate(returnDate);

        // Calcular tarifa por demora si hay retraso
        if (returnDate.isAfter(reservation.getExpectedReturnDate())) {
            long daysLate = ChronoUnit.DAYS.between(reservation.getExpectedReturnDate(), returnDate);
            if (daysLate > 0) {
                BigDecimal lateFee = calculateLateFee(reservation.getBook().getPrice(), daysLate);
                reservation.setLateFee(lateFee);
                log.info("Multa aplicada por {} días de demora: ${}", daysLate, lateFee);
            }
        } else {
            reservation.setLateFee(BigDecimal.ZERO);
        }

        reservation.setStatus(Reservation.ReservationStatus.RETURNED);
        Reservation updatedReservation = reservationRepository.save(reservation);

        // Aumentar la cantidad disponible
        Book book = reservation.getBook();
        book.setAvailableQuantity(book.getAvailableQuantity() + 1);
        bookRepository.save(book);

        log.info("Devolución procesada exitosamente para reserva ID: {}", reservationId);
        return convertToDTO(updatedReservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByActualReturnDateIsNull().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {
        LocalDate currentDate = LocalDate.now();
        return reservationRepository.findByExpectedReturnDateBeforeAndActualReturnDateIsNull(currentDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
        // TODO: Implementar el cálculo del total de la reserva

    protected BigDecimal calculateTotalFee(BigDecimal dailyRate, Integer rentalDays) {
        return dailyRate.multiply(new BigDecimal(rentalDays))
                .setScale(2, RoundingMode.HALF_UP);
    }

    protected BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        // 15% del precio del libro por cada día de demora
        BigDecimal dailyLateFee = bookPrice.multiply(LATE_FEE_PERCENTAGE);
        return dailyLateFee.multiply(new BigDecimal(daysLate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setDailyRate(reservation.getDailyRate());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());

        BigDecimal totalFee = reservation.getTotalFee();
        if (reservation.getLateFee() != null) {
            totalFee = totalFee.add(reservation.getLateFee());
        }
        dto.setTotalFee(totalFee);

        return dto;
    }

    // Método auxiliar para obtener el total de multas pendientes de un usuario
    @Transactional(readOnly = true)
    public BigDecimal getUserPendingLateFees(Long userId) {
        List<Reservation> userReservations = reservationRepository.findByUserId(userId);
        return userReservations.stream()
                .filter(r -> r.getLateFee() != null && r.getLateFee().compareTo(BigDecimal.ZERO) > 0)
                .map(Reservation::getLateFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Método para verificar disponibilidad de un libro
    @Transactional(readOnly = true)
    public boolean isBookAvailable(Long bookExternalId) {
        Book book = bookRepository.findByExternalId(bookExternalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado"));

        long activeReservations = reservationRepository.countByBookExternalIdAndActualReturnDateIsNull(bookExternalId);

        return book.getAvailableQuantity() > activeReservations;
    }

    // Método para calcular el total final (tarifa base + multa)
    public BigDecimal calculateFinalTotal(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        BigDecimal total = reservation.getTotalFee() != null ? reservation.getTotalFee() : BigDecimal.ZERO;
        BigDecimal lateFee = reservation.getLateFee() != null ? reservation.getLateFee() : BigDecimal.ZERO;

        return total.add(lateFee);
    }
}