package com.example.libreria.repository;

import com.example.libreria.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // Encontrar todas las reservas de un usuario específico
    List<Reservation> findByUserId(Long userId);

    // Encontrar reservas activas (aquellas que no han sido devueltas)
    List<Reservation> findByActualReturnDateIsNull();

    // Encontrar reservas vencidas (fecha de fin pasada y no devueltas)
    List<Reservation> findByExpectedReturnDateBeforeAndActualReturnDateIsNull(LocalDate currentDate);

    // Verificar si existe una reserva activa para un libro y usuario específicos
    boolean existsByUserIdAndBookExternalIdAndActualReturnDateIsNull(Long userId, Long bookExternalId);

    // Contar reservas activas por libro
    long countByBookExternalIdAndActualReturnDateIsNull(Long bookExternalId);
}

