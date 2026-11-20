package com.oyl.ms_pedidos.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Un producto dentro del pedido: cuál y cuántos
public record ItemPedidoRequest(
        @NotNull(message = "Falta el producto") Long productoId,
        @NotNull(message = "Falta la cantidad")
        @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad
) {}