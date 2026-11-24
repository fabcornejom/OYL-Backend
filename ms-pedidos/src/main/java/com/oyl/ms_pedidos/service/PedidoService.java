package com.oyl.ms_pedidos.service;

import com.oyl.ms_pedidos.client.ProductoClient;
import com.oyl.ms_pedidos.model.DetallePedido;
import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.model.Pedido;
import com.oyl.ms_pedidos.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class PedidoService {

    private final PedidoRepository repo;
    private final ProductoClient productoClient;

    public PedidoService(PedidoRepository repo, ProductoClient productoClient) {
        this.repo = repo;
        this.productoClient = productoClient;
    }

    // =====================================================
    // LEER
    // =====================================================
    public List<Pedido> listar(EstadoPedido estado) {
        return (estado == null)
                ? repo.findAllByOrderByFechaDesc()
                : repo.findByEstadoOrderByFechaDesc(estado);
    }

    public List<Pedido> listarPorUsuario(Long usuarioId) {
        return repo.findByUsuarioIdOrderByFechaDesc(usuarioId);
    }

    public Pedido buscarPorId(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El pedido " + id + " no existe"));
    }

    // =====================================================
    // CAMBIAR ESTADO (solo avanza de a un paso)
    // PENDIENTE_VALIDACION → CONFIRMADO → PREPARADO → ENTREGADO
    // =====================================================
    public Pedido cambiarEstado(Long id, EstadoPedido nuevo) {
        Pedido pedido = buscarPorId(id);
        EstadoPedido actual = pedido.getEstado();

        if (nuevo.ordinal() != actual.ordinal() + 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede pasar de " + actual + " a " + nuevo);
        }
        pedido.setEstado(nuevo);
        return repo.save(pedido);
    }

    // =====================================================
    // CANCELAR (solo si aún está pendiente) → devuelve el stock
    // =====================================================
    public void cancelar(Long id) {
        Pedido pedido = buscarPorId(id);

        if (pedido.getEstado() != EstadoPedido.PENDIENTE_VALIDACION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden cancelar pedidos pendientes de validación");
        }
        if (pedido.getDetalles() == null || pedido.getDetalles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pedido no tiene cantidades válidas para devolver");
        }
        Map<Long, Integer> aDevolver = new LinkedHashMap<>();
        for (DetallePedido d : pedido.getDetalles()) {
            if (d == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El pedido contiene un producto sin cantidad válida");
            }
            acumularCantidad(aDevolver, d.getProductoId(), d.getCantidad(), HttpStatus.CONFLICT);
        }
        devolverStock(aDevolver);
        repo.delete(pedido);
    }

    // Validar toda la agrupación antes de descontar o devolver existencias.
    private void acumularCantidad(Map<Long, Integer> cantidades, Long productoId,
                                  Integer cantidad, HttpStatus estadoError) {
        if (productoId == null || cantidad == null || cantidad <= 0) {
            throw new ResponseStatusException(estadoError,
                    "El pedido contiene un producto o una cantidad inválidos");
        }
        long acumulada = (long) cantidades.getOrDefault(productoId, 0) + cantidad;
        if (acumulada > Integer.MAX_VALUE) {
            throw new ResponseStatusException(estadoError,
                    "La cantidad total del producto " + productoId + " supera el máximo permitido");
        }
        cantidades.put(productoId, (int) acumulada);
    }

    // Suma de vuelta el stock. Si una devolución falla, sigue con las demás
    private void devolverStock(Map<Long, Integer> cantidades) {
        for (var entrada : cantidades.entrySet()) {
            try {
                productoClient.ajustarStock(entrada.getKey(), entrada.getValue());
            } catch (RuntimeException e) {
                System.err.println("No se pudo devolver stock del producto "
                        + entrada.getKey() + ": " + e.getMessage());
            }
        }
    }
}