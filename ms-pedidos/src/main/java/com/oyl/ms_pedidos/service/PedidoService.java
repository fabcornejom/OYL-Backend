package com.oyl.ms_pedidos.service;

import com.oyl.ms_pedidos.client.ProductoClient;
import com.oyl.ms_pedidos.client.ProductoClient.ProductoDto;
import com.oyl.ms_pedidos.dto.request.CrearPedidoRequest;
import com.oyl.ms_pedidos.dto.request.ItemPedidoRequest;
import com.oyl.ms_pedidos.model.DetallePedido;
import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.model.Pedido;
import com.oyl.ms_pedidos.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class PedidoService {

    // El modelo de pedido de Android representa el total mediante Int.
    private static final long MAXIMO_TOTAL_PEDIDO = Integer.MAX_VALUE;

    private final PedidoRepository repo;
    private final ProductoClient productoClient;

    public PedidoService(PedidoRepository repo, ProductoClient productoClient) {
        this.repo = repo;
        this.productoClient = productoClient;
    }

    // =====================================================
    // CREAR PEDIDO
    // =====================================================
    public Pedido crear(CrearPedidoRequest req) {

        // 0. Juntar productos repetidos: si viene Almendras 2 y Almendras 1 → Almendras 3
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El pedido debe tener al menos un producto");
        }
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (ItemPedidoRequest item : req.items()) {
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El pedido contiene un producto sin cantidad válida");
            }
            acumularCantidad(cantidades, item.productoId(), item.cantidad(), HttpStatus.BAD_REQUEST);
        }

        // 1. VALIDAR: consultar cada producto en ms-productos (precio y stock reales)
        Map<Long, ProductoDto> productos = new LinkedHashMap<>();
        long totalValidado = 0;
        for (var entrada : cantidades.entrySet()) {
            ProductoDto p = productoClient.obtener(entrada.getKey());   // 404 si no existe

            if (p == null || p.precio() == null || p.precio() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El catálogo devolvió un precio de producto inválido");
            }
            if (!Boolean.TRUE.equals(p.disponible())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        p.nombre() + " no está disponible");
            }
            if (p.stock() < entrada.getValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente de " + p.nombre() + ": quedan " + p.stock());
            }
            // Dos int positivos caben multiplicados en long. Comprobar antes de sumar
            // mantiene el acumulado dentro del límite y evita también su desbordamiento.
            long subtotal = (long) p.precio() * entrada.getValue();
            if (subtotal > MAXIMO_TOTAL_PEDIDO - totalValidado) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El total del pedido supera el máximo permitido de " + MAXIMO_TOTAL_PEDIDO);
            }
            totalValidado += subtotal;
            productos.put(p.id(), p);
        }

        // 2. DESCONTAR STOCK, recordando lo ya descontado por si hay que devolverlo
        Map<Long, Integer> descontados = new LinkedHashMap<>();
        try {
            for (var entrada : cantidades.entrySet()) {
                productoClient.ajustarStock(entrada.getKey(), -entrada.getValue());
                descontados.put(entrada.getKey(), entrada.getValue());
            }

            // 3. ARMAR Y GUARDAR EL PEDIDO
            Pedido pedido = new Pedido();
            pedido.setUsuarioId(req.usuarioId());
            // Pedidos nuevos en UTC: Android interpreta así este formato sin offset.
            pedido.setFecha(LocalDateTime.now(ZoneOffset.UTC));
            pedido.setEstado(EstadoPedido.PENDIENTE_VALIDACION);

            for (var entrada : cantidades.entrySet()) {
                ProductoDto p = productos.get(entrada.getKey());

                DetallePedido d = new DetallePedido();
                d.setProductoId(p.id());
                d.setNombreProducto(p.nombre());
                d.setPrecioUnitario(p.precio());     // El precio lo pone el SERVIDOR
                d.setCantidad(entrada.getValue());

                pedido.agregarDetalle(d);
            }
            pedido.setTotal(totalValidado);

            return repo.save(pedido);   // Guarda el pedido y sus detalles (cascade)

        } catch (RuntimeException error) {
            // 4. COMPENSAR: algo falló a la mitad → devolver el stock ya descontado
            devolverStock(descontados);
            throw error;
        }
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