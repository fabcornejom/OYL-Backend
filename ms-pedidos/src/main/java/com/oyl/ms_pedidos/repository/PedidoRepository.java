package com.oyl.ms_pedidos.repository;

import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Historial de un cliente, del más nuevo al más antiguo
    List<Pedido> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    // Para el admin: filtrar por estado (ej. pendientes)
    List<Pedido> findByEstadoOrderByFechaDesc(EstadoPedido estado);

    // Todos, del más nuevo al más antiguo
    List<Pedido> findAllByOrderByFechaDesc();
}