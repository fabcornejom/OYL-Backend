package com.oyl.ms_productos.repository;

import com.oyl.ms_productos.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// JpaRepository<Producto, Long> = repositorio de Productos cuyo id es Long
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // Spring arma el SQL leyendo el nombre del método:
    // SELECT COUNT(*) > 0 FROM productos WHERE codigo = ?
    boolean existsByCodigo(String codigo);

    // SELECT * FROM productos WHERE disponible = true
    List<Producto> findByDisponibleTrue();

    // SELECT * FROM productos WHERE LOWER(categoria) = LOWER(?)
    List<Producto> findByCategoriaIgnoreCase(String categoria);

    // Consulta escrita a mano (JPQL): productos con stock bajo, para el admin
    @Query("SELECT p FROM Producto p WHERE p.stock <= p.stockMinimo")
    List<Producto> buscarStockBajo();
}