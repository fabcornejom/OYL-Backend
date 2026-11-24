package com.oyl.ms_pedidos.controller;

import com.oyl.ms_pedidos.dto.request.CambiarEstadoRequest;
import com.oyl.ms_pedidos.dto.request.CrearPedidoRequest;
import com.oyl.ms_pedidos.dto.response.PedidoResponse;
import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.service.PedidoService;
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

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    // POST /api/pedidos  → el cliente confirma la compra
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENTE')")
    public PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest req, @AuthenticationPrincipal Jwt token) {
        if (!req.usuarioId().toString().equals(token.getSubject())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return PedidoResponse.desde(service.crear(req));
    }

    // GET /api/pedidos                         → todos (admin)
    // GET /api/pedidos?estado=PENDIENTE_VALIDACION → filtrados por estado
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado) {
        return service.listar(estado).stream().map(PedidoResponse::desde).toList();
    }

    // GET /api/pedidos/usuario/2  → historial de un cliente
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CLIENTE') and #usuarioId.toString() == authentication.name)")
    public List<PedidoResponse> porUsuario(@PathVariable Long usuarioId) {
        return service.listarPorUsuario(usuarioId).stream().map(PedidoResponse::desde).toList();
    }

    // GET /api/pedidos/15  → detalle de un pedido
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public PedidoResponse buscar(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        return PedidoResponse.desde(pedidoAutorizado(id, token));
    }

    // PATCH /api/pedidos/15/estado  {"estado": "CONFIRMADO"}  → el admin avanza el estado
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public PedidoResponse cambiarEstado(@PathVariable Long id,
                                        @Valid @RequestBody CambiarEstadoRequest req) {
        return PedidoResponse.desde(service.cambiarEstado(id, req.estado()));
    }

    // DELETE /api/pedidos/15  → cancelar (solo pendientes; devuelve el stock)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public void cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        pedidoAutorizado(id, token);
        service.cancelar(id);
    }

    private Pedido pedidoAutorizado(Long id, Jwt token) {
        Pedido pedido = service.buscarPorId(id);
        if (!"ADMIN".equals(token.getClaimAsString("role")) && !pedido.getUsuarioId().toString().equals(token.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return pedido;
    }

}