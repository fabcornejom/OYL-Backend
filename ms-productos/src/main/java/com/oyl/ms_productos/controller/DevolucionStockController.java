package com.oyl.ms_productos.controller;
import com.oyl.ms_productos.service.DevolucionStockService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/productos/devoluciones-stock")
public class DevolucionStockController {
    private final DevolucionStockService service;
    public DevolucionStockController(DevolucionStockService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('STOCK_SERVICE')")
    public DevolucionStockService.Resultado aplicar(@RequestBody DevolucionStockService.Solicitud solicitud) {
        return service.aplicar(solicitud);
    }
}
