package com.oyl.ms_productos.repository;

import com.oyl.ms_productos.model.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// JpaRepository<Producto, Long> = repositorio de Productos cuyo id es Long
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // El bloqueo coordina la edición de la ficha con los movimientos de stock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> buscarPorIdConBloqueo(@Param("id") Long id);

    // Spring arma el SQL leyendo el nombre del método:
    // SELECT COUNT(*) > 0 FROM productos WHERE codigo = ?
    boolean existsByCodigo(String codigo);

    // SELECT * FROM productos WHERE disponible = true
    List<Producto> findByDisponibleTrueAndEliminadoFalse();

    List<Producto> findByEliminadoFalse();

    Optional<Producto> findByIdAndEliminadoFalse(Long id);

    // SELECT * FROM productos WHERE LOWER(categoria) = LOWER(?)
    List<Producto> findByCategoriaIgnoreCaseAndEliminadoFalse(String categoria);

    // Consulta escrita a mano (JPQL): productos con stock bajo, para el admin
    @Query("SELECT p FROM Producto p WHERE p.eliminado = false AND p.stock <= p.stockMinimo")
    List<Producto> buscarStockBajo();
}