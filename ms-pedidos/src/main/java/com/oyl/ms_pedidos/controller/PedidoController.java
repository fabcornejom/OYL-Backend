package com.oyl.ms_pedidos.controller;

import com.oyl.ms_pedidos.dto.request.CambiarEstadoRequest;
import com.oyl.ms_pedidos.dto.request.CrearPedidoRequest;
import com.oyl.ms_pedidos.dto.response.PedidoResponse;
import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.service.PedidoService;
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
    public PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest req) {
        return PedidoResponse.desde(service.crear(req));
    }

    // GET /api/pedidos                         → todos (admin)
    // GET /api/pedidos?estado=PENDIENTE_VALIDACION → filtrados por estado
    @GetMapping
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado) {
        return service.listar(estado).stream().map(PedidoResponse::desde).toList();
    }

    // GET /api/pedidos/usuario/2  → historial de un cliente
    @GetMapping("/usuario/{usuarioId}")
    public List<PedidoResponse> porUsuario(@PathVariable Long usuarioId) {
        return service.listarPorUsuario(usuarioId).stream().map(PedidoResponse::desde).toList();
    }

    // GET /api/pedidos/15  → detalle de un pedido
    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable Long id) {
        return PedidoResponse.desde(service.buscarPorId(id));
    }

    // PATCH /api/pedidos/15/estado  {"estado": "CONFIRMADO"}  → el admin avanza el estado
    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id,
                                        @Valid @RequestBody CambiarEstadoRequest req) {
        return PedidoResponse.desde(service.cambiarEstado(id, req.estado()));
    }

    // DELETE /api/pedidos/15  → cancelar (solo pendientes; devuelve el stock)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id) {
        service.cancelar(id);
    }
}