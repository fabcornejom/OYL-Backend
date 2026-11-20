package com.oyl.ms_pedidos.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;


// Lo que manda la app al confirmar la compra
public record CrearPedidoRequest(
        @NotNull(message = "Falta el usuario") Long usuarioId,
        @NotEmpty(message = "El pedido debe tener al menos un producto")
        List<@Valid ItemPedidoRequest> items
) {}