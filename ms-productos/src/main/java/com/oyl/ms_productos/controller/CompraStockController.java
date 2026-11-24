package com.oyl.ms_productos.controller;
import com.oyl.ms_productos.service.CompraStockService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/productos/compras-stock")
public class CompraStockController {
    private final CompraStockService service;
    public CompraStockController(CompraStockService service) { this.service=service; }
    @PostMapping
    @PreAuthorize("hasRole('STOCK_SERVICE')")
    public CompraStockService.Resultado aplicar(@RequestBody CompraStockService.Solicitud solicitud) {
        return service.aplicar(solicitud);
    }
}
