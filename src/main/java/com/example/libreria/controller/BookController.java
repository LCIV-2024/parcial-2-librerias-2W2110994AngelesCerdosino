package com.example.libreria.controller;

import com.example.libreria.dto.BookResponseDTO;
import com.example.libreria.service.BookService;
import com.example.libreria.service.ExternalBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {
    
    private final BookService bookService;
    private final ExternalBookService externalBookService;
    
    @PostMapping("/sync")
    public ResponseEntity<String> syncBooks() {
        bookService.syncBooksFromExternalApi();
        return ResponseEntity.ok("Libros sincronizados exitosamente desde la API externa");
    }
    
    @GetMapping
    public ResponseEntity<List<BookResponseDTO>> getAllBooks() {
        List<BookResponseDTO> books = bookService.getAllBooks();
        return ResponseEntity.ok(books);
    }
    
    @GetMapping("/{externalId}")
    public ResponseEntity<BookResponseDTO> getBookByExternalId(@PathVariable Long externalId) {
        BookResponseDTO book = bookService.getBookByExternalId(externalId);
        return ResponseEntity.ok(book);
    }
    
    @PutMapping("/{externalId}/stock")
    public ResponseEntity<BookResponseDTO> updateStock(
            @PathVariable Long externalId,
            @RequestParam Integer stockQuantity) {
        BookResponseDTO book = bookService.updateStock(externalId, stockQuantity);
        return ResponseEntity.ok(book);
    }

    @GetMapping("/external/availability")
    public ResponseEntity<Map<String, Object>> checkExternalApiAvailability() {
        try {
            boolean isAvailable = externalBookService.isExternalApiAvailable();
            Map<String, Object> response = new HashMap<>();
            response.put("available", isAvailable);
            response.put("timestamp", LocalDateTime.now());
            response.put("message", isAvailable ?
                    "API externa disponible" : "API externa no disponible");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("available", false);
            errorResponse.put("timestamp", LocalDateTime.now());
            errorResponse.put("error", "Error al verificar disponibilidad: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

