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
    private final FotoProductoService fotos;

    // Inyección por constructor: Spring pasa el repositorio automáticamente
    public ProductoService(ProductoRepository repo, FotoProductoService fotos) {
        this.repo = repo;
        this.fotos = fotos;
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
    @Transactional
    public Producto crear(Producto producto) {
        fotos.validarReferencia(producto.getImagenUrl(), null);
        if (repo.existsByCodigo(producto.getCodigo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un producto con el código " + producto.getCodigo());
        }
        producto.setId(null);   // El id lo genera PostgreSQL, nunca el cliente
        return repo.save(producto);
    }

    // ---------- ACTUALIZAR ----------
    @Transactional
    public Producto actualizar(Long id, Producto datos) {
        Producto actual = buscarPorIdConBloqueo(id);
        fotos.validarReferencia(datos.getImagenUrl(), actual.getImagenUrl());
        return actualizarFicha(actual, datos, true);
    }

    private Producto actualizarFicha(Producto actual, Producto datos, boolean cambiarReferencia) {

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
        // PUT conserva el stock leído bajo bloqueo; solo PATCH /stock cambia existencias.
        actual.setStockMinimo(datos.getStockMinimo());
        actual.setDisponible(datos.getDisponible());
        if (cambiarReferencia && !java.util.Objects.equals(actual.getImagenUrl(), datos.getImagenUrl())) {
            fotos.quitar(actual.getId());
            actual.setImagenUrl(datos.getImagenUrl());
        }

        return repo.save(actual);   // save con id existente = UPDATE
    }

    @Transactional
    public Producto crearConFoto(Producto producto, FotoProductoService.Preparada foto) {
        producto.setImagenUrl(null);
        Producto nuevo = crear(producto);
        repo.flush();
        nuevo.setImagenUrl(fotos.guardar(nuevo.getId(), foto));
        return repo.saveAndFlush(nuevo);
    }

    @Transactional
    public Producto actualizarConFoto(Long id, Producto datos, FotoProductoService.Preparada foto) {
        Producto actual = buscarPorIdConBloqueo(id);
        actualizarFicha(actual, datos, false);
        actual.setImagenUrl(fotos.guardar(id, foto));
        return repo.saveAndFlush(actual);
    }

    // ---------- ELIMINAR ----------
    @Transactional
    public void eliminar(Long id) {
        Producto actual = buscarPorIdConBloqueo(id);
        repo.delete(actual); // La FK elimina también el archivo binario en la misma transacción.
        repo.flush();
    }

    // ---------- STOCK ----------
    // cambio positivo = entrada (reponer); negativo = salida (venta)
    @Transactional
    public Producto ajustarStock(Long id, int cambio) {
        Producto producto = buscarPorIdConBloqueo(id);
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

    // Ambos escritores usan el mismo bloqueo hasta que finaliza su transacción.
    private Producto buscarPorIdConBloqueo(Long id) {
        return repo.buscarPorIdConBloqueo(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El producto " + id + " no existe"));
    }

}