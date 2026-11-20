package com.oyl.ms_pedidos.dto.response;

import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.model.Pedido;

import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        Long usuarioId,
        LocalDateTime fecha,
        EstadoPedido estado,
        Long total,
        List<DetalleResponse> detalles
) {
    public static PedidoResponse desde(Pedido p) {
        return new PedidoResponse(p.getId(), p.getUsuarioId(), p.getFecha(),
                p.getEstado(), p.getTotal(),
                p.getDetalles().stream().map(DetalleResponse::desde).toList());
    }
}