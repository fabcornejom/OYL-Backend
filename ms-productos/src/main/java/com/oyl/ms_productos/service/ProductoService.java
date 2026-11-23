package com.oyl.ms_productos.service;

import com.oyl.ms_productos.model.Producto;
import com.oyl.ms_productos.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service   // Spring crea una sola instancia y la entrega a quien la necesite
public class ProductoService {

    private final ProductoRepository repo;

    // Inyección por constructor: Spring pasa el repositorio automáticamente
    public ProductoService(ProductoRepository repo) {
        this.repo = repo;
    }

    // ---------- LEER ----------
    public List<Producto> listar(boolean soloDisponibles) {
        return soloDisponibles ? repo.findByDisponibleTrue() : repo.findAll();
    }

    public Producto buscarPorId(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El producto " + id + " no existe"));
    }

    public List<Producto> stockBajo() {
        return repo.buscarStockBajo();
    }

    // ---------- CREAR ----------
    public Producto crear(Producto producto) {
        if (repo.existsByCodigo(producto.getCodigo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un producto con el código " + producto.getCodigo());
        }
        producto.setId(null);   // El id lo genera PostgreSQL, nunca el cliente
        return repo.save(producto);
    }

    // ---------- ACTUALIZAR ----------
    public Producto actualizar(Long id, Producto datos) {
        Producto actual = buscarPorId(id);   // Si no existe, lanza 404

        // Si cambió el código, revisar que el nuevo no esté usado por otro
        if (!actual.getCodigo().equals(datos.getCodigo())
                && repo.existsByCodigo(datos.getCodigo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un producto con el código " + datos.getCodigo());
        }

        actual.setNombre(datos.getNombre());
        actual.setCodigo(datos.getCodigo());
        actual.setCategoria(datos.getCategoria());
        actual.setDescripcion(datos.getDescripcion());
        actual.setPresentacion(datos.getPresentacion());
        actual.setPrecio(datos.getPrecio());
        actual.setStock(datos.getStock());
        actual.setStockMinimo(datos.getStockMinimo());
        actual.setDisponible(datos.getDisponible());
        actual.setImagenUrl(datos.getImagenUrl());

        return repo.save(actual);   // save con id existente = UPDATE
    }

    // ---------- ELIMINAR ----------
    public void eliminar(Long id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El producto " + id + " no existe");
        }
        repo.deleteById(id);
    }

    // ---------- STOCK ----------
    // cambio positivo = entrada (reponer); negativo = salida (venta)
    @Transactional
    public Producto ajustarStock(Long id, int cambio) {
        Producto producto = repo.buscarPorIdParaAjustarStock(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El producto " + id + " no existe"));
        // Calcular en long evita que una suma válida como int cambie de signo.
        long nuevoStock = (long) producto.getStock() + cambio;

        if (nuevoStock < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuficiente de " + producto.getNombre()
                            + ": quedan " + producto.getStock());
        }
        if (nuevoStock > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El stock supera el máximo permitido");
        }
        producto.setStock((int) nuevoStock);
        return repo.save(producto);
    }
}