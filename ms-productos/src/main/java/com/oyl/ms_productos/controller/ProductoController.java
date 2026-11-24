package com.oyl.ms_productos.controller;

import com.oyl.ms_productos.model.Producto;
import com.oyl.ms_productos.service.ProductoService;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController                    // Responde en JSON
@RequestMapping("/api/productos")  // Dirección base de todos los endpoints
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    // GET /api/productos              → todos
    // GET /api/productos?disponibles=true → solo los activos (para el cliente)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public List<Producto> listar(@RequestParam(defaultValue = "false") boolean disponibles) {
        return service.listar(disponibles);
    }

    // GET /api/productos/stock-bajo → alertas para el admin
    @GetMapping("/stock-bajo")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Producto> stockBajo() {
        return service.stockBajo();
    }

    // GET /api/productos/5
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE','STOCK_SERVICE')")
    public Producto buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    // POST /api/productos  + JSON en el cuerpo
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)   // Responde 201 en vez de 200
    @PreAuthorize("hasRole('ADMIN')")
    public Producto crear(@Valid @RequestBody Producto producto) {
        return service.crear(producto);
    }

    // PUT /api/productos/5  + JSON en el cuerpo
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Producto actualizar(@PathVariable Long id, @Valid @RequestBody Producto producto) {
        return service.actualizar(id, producto);
    }

    // DELETE /api/productos/5
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204: borrado, sin contenido que devolver
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }

    // PATCH /api/productos/5/stock  + {"cambio": -2}
    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','STOCK_SERVICE')")
    public Producto ajustarStock(@PathVariable Long id, @Valid @RequestBody AjusteStock ajuste) {
        return service.ajustarStock(id, ajuste.cambio());
    }

    // Cuerpo del PATCH: solo trae cuánto cambia el stock
    public record AjusteStock(@NotNull Integer cambio) {}
}