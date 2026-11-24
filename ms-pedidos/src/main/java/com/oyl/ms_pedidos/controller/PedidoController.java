package com.oyl.ms_pedidos.controller;

import com.oyl.ms_pedidos.dto.request.CambiarEstadoRequest;
import com.oyl.ms_pedidos.dto.request.CrearPedidoRequest;
import com.oyl.ms_pedidos.dto.response.PedidoResponse;
import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.service.PedidoService;
import com.oyl.ms_pedidos.service.SolicitudCompraService;
import com.oyl.ms_pedidos.service.CancelacionPedidoService;
import com.oyl.ms_pedidos.model.Pedido;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;

    private final SolicitudCompraService compras;
    private final CancelacionPedidoService cancelaciones;
    public PedidoController(PedidoService service, SolicitudCompraService compras, CancelacionPedidoService cancelaciones) {
        this.service = service; this.compras = compras; this.cancelaciones = cancelaciones;
    }

    // POST devuelve el resultado durable del intento (completado o rechazado).
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public SolicitudCompraService.Resultado crear(@RequestHeader("Idempotency-Key") String clave, @Valid @RequestBody CrearPedidoRequest req, @AuthenticationPrincipal Jwt token) {
        if (!req.usuarioId().toString().equals(token.getSubject())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return compras.crear(clave, req);
    }

    @GetMapping("/solicitudes/{clave}")
    @PreAuthorize("hasRole('CLIENTE')")
    public SolicitudCompraService.Resultado consultarSolicitud(@PathVariable String clave, @AuthenticationPrincipal Jwt token) {
        return compras.consultar(Long.valueOf(token.getSubject()), clave);
    }

    // GET /api/pedidos                         → todos (admin)
    // GET /api/pedidos?estado=PENDIENTE_VALIDACION → filtrados por estado
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado) {
        var pendientes = cancelaciones.idsEnRevision();
        return service.listar(estado).stream().map(p -> PedidoResponse.desde(p, pendientes.contains(p.getId()))).toList();
    }

    // GET /api/pedidos/usuario/2  → historial de un cliente
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CLIENTE') and #usuarioId.toString() == authentication.name)")
    public List<PedidoResponse> porUsuario(@PathVariable Long usuarioId) {
        var pendientes = cancelaciones.idsEnRevision();
        return service.listarPorUsuario(usuarioId).stream().map(p -> PedidoResponse.desde(p, pendientes.contains(p.getId()))).toList();
    }

    // GET /api/pedidos/15  → detalle de un pedido
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public PedidoResponse buscar(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        Pedido pedido = pedidoAutorizado(id, token);
        return PedidoResponse.desde(pedido, cancelaciones.idsEnRevision().contains(id));
    }

    // PATCH /api/pedidos/15/estado  {"estado": "CONFIRMADO"}  → el admin avanza el estado
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public PedidoResponse cambiarEstado(@PathVariable Long id,
                                        @Valid @RequestBody CambiarEstadoRequest req) {
        return PedidoResponse.desde(service.cambiarEstado(id, req.estado()));
    }

    @PostMapping("/{id}/cancelacion")
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public CancelacionPedidoService.Resultado continuarCancelacion(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        return cancelaciones.continuar(id, Long.valueOf(token.getSubject()), "ADMIN".equals(token.getClaimAsString("role")));
    }

    @GetMapping("/{id}/cancelacion")
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public CancelacionPedidoService.Resultado consultarCancelacion(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        return cancelaciones.consultar(id, Long.valueOf(token.getSubject()), "ADMIN".equals(token.getClaimAsString("role")));
    }

    // Compatibilidad: DELETE usa la misma operación durable y la misma autorización.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public org.springframework.http.ResponseEntity<CancelacionPedidoService.Resultado> cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        var resultado = continuarCancelacion(id, token);
        HttpStatus estado = switch (resultado.estado()) {
            case "COMPLETADA" -> HttpStatus.OK;
            case "RECHAZADA" -> HttpStatus.CONFLICT;
            default -> HttpStatus.ACCEPTED;
        };
        return org.springframework.http.ResponseEntity.status(estado).body(resultado);
    }

    private Pedido pedidoAutorizado(Long id, Jwt token) {
        Pedido pedido = service.buscarPorId(id);
        if (!"ADMIN".equals(token.getClaimAsString("role")) && !pedido.getUsuarioId().toString().equals(token.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return pedido;
    }

}