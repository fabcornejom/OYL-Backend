package com.oyl.ms_pedidos.dto.response;

import com.oyl.ms_pedidos.model.DetallePedido;

public record DetalleResponse(
        Long productoId,
        String nombreProducto,
        Integer precioUnitario,
        Integer cantidad,
        Long subtotal
) {
    public static DetalleResponse desde(DetallePedido d) {
        return new DetalleResponse(d.getProductoId(), d.getNombreProducto(),
                d.getPrecioUnitario(), d.getCantidad(), d.getSubtotal());
    }
}