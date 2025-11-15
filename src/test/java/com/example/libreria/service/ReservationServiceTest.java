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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReservationService reservationService;

    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    private ReservationRequestDTO reservationRequestDTO;
    private UserResponseDTO userResponseDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");

        testBook = new Book();
        testBook.setExternalId(1L);
        testBook.setExternalId(258027L);
        testBook.setTitle("El Gran Libro");
        testBook.setAuthorName(Arrays.asList("Autor Test"));
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setAvailableQuantity(5);
        testBook.setStockQuantity(10);

        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setDailyRate(testBook.getPrice());
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());

        reservationRequestDTO = new ReservationRequestDTO();
        reservationRequestDTO.setUserId(1L);
        reservationRequestDTO.setBookExternalId(258027L);
        reservationRequestDTO.setRentalDays(7);
        reservationRequestDTO.setStartDate(LocalDate.now());

        userResponseDTO = new UserResponseDTO();
        userResponseDTO.setId(1L);
        userResponseDTO.setName("Juan Pérez");
        userResponseDTO.setEmail("juan@example.com");
    }

    @Test
    void createReservation_Success() {
        when(userService.getUserById(1L)).thenReturn(userResponseDTO);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.existsByUserIdAndBookExternalIdAndActualReturnDateIsNull(1L, 258027L)).thenReturn(false);
        when(userService.getUserEntityById(1L)).thenReturn(testUser);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        when(bookRepository.save(any(Book.class))).thenReturn(testBook);

        ReservationResponseDTO result = reservationService.createReservation(reservationRequestDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals(258027L, result.getBookExternalId()); // CAMBIADO: de String a Long
        assertEquals(7, result.getRentalDays());

        verify(bookRepository).save(any(Book.class));
        verify(reservationRepository).save(any(Reservation.class));
        verify(bookRepository).findByExternalId(258027L);
    }

    @Test
    void createReservation_UserNotFound() {

        when(userService.getUserById(1L)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.createReservation(reservationRequestDTO));

        assertEquals("Usuario no encontrado con ID: 1", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void createReservation_BookNotFound() {
        when(userService.getUserById(1L)).thenReturn(userResponseDTO);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.createReservation(reservationRequestDTO));

        assertEquals("Libro no encontrado con ID externo: 258027", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void createReservation_BookNotAvailable() {

        testBook.setAvailableQuantity(0);
        when(userService.getUserById(1L)).thenReturn(userResponseDTO);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));


        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.createReservation(reservationRequestDTO));

        assertEquals("Libro no disponible. Stock actual: 0", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void createReservation_UserHasActiveReservation() {

        when(userService.getUserById(1L)).thenReturn(userResponseDTO);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.existsByUserIdAndBookExternalIdAndActualReturnDateIsNull(1L, 258027L)).thenReturn(true);


        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.createReservation(reservationRequestDTO));

        assertEquals("El usuario ya tiene una reserva activa para este libro", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void returnBook_OnTime() {
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now().plusDays(7)); // Devuelve a tiempo

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        when(bookRepository.save(any(Book.class))).thenReturn(testBook);

        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getLateFee());

        verify(reservationRepository).save(any(Reservation.class));
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void returnBook_LateReturn() {
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now().plusDays(10)); // 3 días tarde

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        when(bookRepository.save(any(Book.class))).thenReturn(testBook);

        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertNotNull(result.getLateFee());
        assertTrue(result.getLateFee().compareTo(BigDecimal.ZERO) > 0);

        verify(reservationRepository).save(any(Reservation.class));
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void returnBook_ReservationNotFound() {

        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now());

        when(reservationRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.returnBook(1L, returnRequest));

        assertEquals("Reserva no encontrada con ID: 1", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void returnBook_AlreadyReturned() {
        testReservation.setStatus(Reservation.ReservationStatus.RETURNED);
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now());

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.returnBook(1L, returnRequest));

        assertEquals("La reserva ya fue devuelta", exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void getReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        ReservationResponseDTO result = reservationService.getReservationById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals(258027L, result.getBookExternalId()); // CAMBIADO: de String a Long
    }

    @Test
    void getReservationById_NotFound() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.getReservationById(1L));

        assertEquals("Reserva no encontrada con ID: 1", exception.getMessage());
    }

    @Test
    void getAllReservations_Success() {
        List<Reservation> reservations = Arrays.asList(testReservation);
        when(reservationRepository.findAll()).thenReturn(reservations);

        List<ReservationResponseDTO> result = reservationService.getAllReservations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void getReservationsByUserId_Success() {
        List<Reservation> reservations = Arrays.asList(testReservation);
        when(reservationRepository.findByUserId(1L)).thenReturn(reservations);

        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getUserId());
    }

    @Test
    void getActiveReservations_Success() {
        List<Reservation> reservations = Arrays.asList(testReservation);
        when(reservationRepository.findByActualReturnDateIsNull()).thenReturn(reservations);

        List<ReservationResponseDTO> result = reservationService.getActiveReservations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Reservation.ReservationStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void getOverdueReservations_Success() {
        List<Reservation> reservations = Arrays.asList(testReservation);
        when(reservationRepository.findByExpectedReturnDateBeforeAndActualReturnDateIsNull(any(LocalDate.class)))
                .thenReturn(reservations);

        List<ReservationResponseDTO> result = reservationService.getOverdueReservations();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void calculateTotalFee_Success() {
        BigDecimal result = reservationService.calculateTotalFee(new BigDecimal("10.00"), 5);

        assertEquals(new BigDecimal("50.00"), result);
    }

    @Test
    void calculateLateFee_Success() {
        BigDecimal result = reservationService.calculateLateFee(new BigDecimal("20.00"), 3);
        // 20.00 * 0.15 * 3 = 9.00
        assertEquals(new BigDecimal("9.00"), result);
    }

    @Test
    void getUserPendingLateFees_Success() {
        testReservation.setLateFee(new BigDecimal("15.00"));
        List<Reservation> reservations = Arrays.asList(testReservation);
        when(reservationRepository.findByUserId(1L)).thenReturn(reservations);

        BigDecimal result = reservationService.getUserPendingLateFees(1L);

        assertEquals(new BigDecimal("15.00"), result);
    }

    @Test
    void getUserPendingLateFees_NoLateFees() {
        testReservation.setLateFee(BigDecimal.ZERO);
        List<Reservation> reservations = Arrays.asList(testReservation);
        when(reservationRepository.findByUserId(1L)).thenReturn(reservations);

        BigDecimal result = reservationService.getUserPendingLateFees(1L);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void isBookAvailable_Available() {
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.countByBookExternalIdAndActualReturnDateIsNull(258027L)).thenReturn(2L);

        boolean result = reservationService.isBookAvailable(258027L);

        assertTrue(result); // availableQuantity=5, activeReservations=2 -> 5 > 2
    }

    @Test
    void isBookAvailable_NotAvailable() {
        testBook.setAvailableQuantity(2);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.countByBookExternalIdAndActualReturnDateIsNull(258027L)).thenReturn(2L);

        boolean result = reservationService.isBookAvailable(258027L);

        assertFalse(result); // availableQuantity=2, activeReservations=2 -> 2 <= 2
    }

    @Test
    void calculateFinalTotal_WithLateFee() {
        // Given
        testReservation.setTotalFee(new BigDecimal("100.00"));
        testReservation.setLateFee(new BigDecimal("15.00"));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        // When
        BigDecimal result = reservationService.calculateFinalTotal(1L);

        // Then
        assertEquals(new BigDecimal("115.00"), result);
    }

    @Test
    void calculateFinalTotal_NoLateFee() {
        // Given
        testReservation.setTotalFee(new BigDecimal("100.00"));
        testReservation.setLateFee(null);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        // When
        BigDecimal result = reservationService.calculateFinalTotal(1L);

        // Then
        assertEquals(new BigDecimal("100.00"), result);
    }
}