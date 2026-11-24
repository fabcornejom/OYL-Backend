package com.oyl.ms_productos.controller;

import com.oyl.ms_productos.service.MovimientoStockService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/productos/movimientos-stock")
@PreAuthorize("hasRole('ADMIN')")
public class MovimientoStockController {
    private final MovimientoStockService servicio;
    public MovimientoStockController(MovimientoStockService servicio) { this.servicio=servicio; }
    @PostMapping("/{clave}")
    public MovimientoStockService.Resultado continuar(@PathVariable UUID clave,
            @RequestBody MovimientoStockService.Solicitud solicitud, Authentication identidad) {
        return servicio.aplicar(actor(identidad),clave,solicitud);
    }
    @GetMapping("/{clave}")
    public MovimientoStockService.Resultado consultar(@PathVariable UUID clave, Authentication identidad) {
        return servicio.consultar(actor(identidad),clave);
    }
    private long actor(Authentication identidad) {
        try {
            long id=Long.parseLong(identidad.getName());
            if (id>0) return id;
        } catch (NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Identidad de inventario inválida");
    }
}
