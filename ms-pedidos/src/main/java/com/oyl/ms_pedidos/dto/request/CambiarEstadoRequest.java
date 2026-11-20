package com.oyl.ms_pedidos.dto.request;

import com.oyl.ms_pedidos.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

// Lo que manda el admin para avanzar el estado: {"estado": "CONFIRMADO"}
public record CambiarEstadoRequest(
        @NotNull(message = "Falta el estado") EstadoPedido estado
) {}