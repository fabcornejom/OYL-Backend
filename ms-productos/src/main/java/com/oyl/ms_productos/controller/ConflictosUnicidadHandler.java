package com.oyl.ms_productos.controller;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Fuera de la transacción: el fallo de unicidad ya provocó su rollback.
@RestControllerAdvice(assignableTypes = { ProductoController.class, FotoProductoController.class })
public class ConflictosUnicidadHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejar(DataIntegrityViolationException error) {
        if (!esDuplicado(error)) throw error;
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Un dato único del producto ya está en uso. Revisa el código y actualiza el catálogo antes de guardar.");
        problema.setTitle("Datos duplicados");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
    }

    private boolean esDuplicado(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            // PostgreSQL distingue unicidad de FK, NOT NULL y otras restricciones.
            if (causa instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
