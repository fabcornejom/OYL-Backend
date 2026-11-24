package com.oyl.ms_pedidos.service;

import com.oyl.ms_pedidos.client.ProductoClient;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CancelacionPedidoService {
    public record Resultado(Long pedidoId, Long usuarioId, String estado, String mensaje) {}
    private final JdbcTemplate jdbc;
    private final ProductoClient productos;
    private final TransactionTemplate tx;

    public CancelacionPedidoService(JdbcTemplate jdbc, ProductoClient productos,
                                    PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.productos = productos;
        tx = new TransactionTemplate(gestor);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
    }

    public Resultado continuar(Long pedidoId, Long actorId, boolean admin) {
        validarId(pedidoId);
        Resultado registro = tx.execute(t -> registrar(pedidoId, actorId, admin));
        if (!"EN_REVISION".equals(registro.estado())) return registro;

        // El registro previo ya está confirmado. Si falla esta fase, no se pierde.
        return tx.execute(t -> {
            limitarEspera();
            var pedido = bloquearPedido(pedidoId);
            var fila = registro(pedidoId, true);
            if (fila == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Falta el registro de cancelación");
            autorizar((Long) fila.get("usuario_id"), actorId, admin);
            if ("COMPLETADA".equals(fila.get("estado"))) return resultado(fila);
            if (pedido == null || !"PENDIENTE_VALIDACION".equals(pedido.get("estado"))) {
                return enRevision(fila, "El pedido requiere revisión del administrador antes de continuar la devolución.");
            }
            var items = Arrays.stream(((String) fila.get("contenido")).split(";"))
                .map(linea -> {
                    var campos = linea.split(":");
                    return new ProductoClient.ItemDevolucion(Long.valueOf(campos[0]), Integer.valueOf(campos[1]));
                }).toList();
            String operacion = "devolucion:pedido:" + pedidoId;
            var devolucion = productos.devolver(operacion, items);
            if (devolucion == null || !operacion.equals(devolucion.operacion())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo verificar la devolución de stock");
            }
            if ("PENDIENTE".equals(devolucion.estado())) {
                return enRevision(fila, devolucion.mensaje());
            }
            if (!"APLICADA".equals(devolucion.estado())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Estado de devolución desconocido");
            }
            // Solo después del comprobante de inventario. El borrado y el resultado son atómicos.
            jdbc.update("DELETE FROM detalles_pedido WHERE pedido_id=?", pedidoId);
            jdbc.update("DELETE FROM pedidos WHERE id=?", pedidoId);
            jdbc.update("UPDATE cancelaciones_pedido SET estado='COMPLETADA',mensaje=? WHERE pedido_id=?",
                "Pedido cancelado y existencias devueltas", pedidoId);
            return new Resultado(pedidoId, (Long) fila.get("usuario_id"), "COMPLETADA", "Pedido cancelado y existencias devueltas");
        });
    }

    private Resultado registrar(Long id, Long actorId, boolean admin) {
        limitarEspera();
        // Todos los escritores bloquean primero pedido y después cancelación.
        var pedido = bloquearPedido(id);
        var fila = registro(id, true);
        if (fila != null) {
            autorizar((Long) fila.get("usuario_id"), actorId, admin);
            return resultado(fila);
        }
        if (pedido == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El pedido no existe");
        Long propietario = (Long) pedido.get("usuario_id");
        autorizar(propietario, actorId, admin);
        if (!"PENDIENTE_VALIDACION".equals(pedido.get("estado"))) {
            return new Resultado(id, propietario, "RECHAZADA", "Solo se pueden cancelar pedidos pendientes de validación. Actualiza el historial.");
        }
        var detalles = jdbc.queryForList("SELECT producto_id,cantidad FROM detalles_pedido WHERE pedido_id=? ORDER BY producto_id", id);
        var cantidades = new TreeMap<Long, Integer>();
        for (var detalle : detalles) {
            Long productoId = (Long) detalle.get("producto_id");
            Integer cantidad = (Integer) detalle.get("cantidad");
            if (productoId == null || productoId <= 0 || cantidad == null || cantidad <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido contiene cantidades inválidas para devolver");
            }
            long total = (long) cantidades.getOrDefault(productoId, 0) + cantidad;
            if (total > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT, "La cantidad del pedido supera el máximo para devolver");
            cantidades.put(productoId, (int) total);
        }
        if (cantidades.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido no tiene artículos para devolver");
        String contenido = cantidades.entrySet().stream().map(i -> i.getKey() + ":" + i.getValue()).collect(Collectors.joining(";"));
        jdbc.update("INSERT INTO cancelaciones_pedido(pedido_id,usuario_id,contenido,estado) VALUES (?,?,?,'EN_REVISION')", id, propietario, contenido);
        return new Resultado(id, propietario, "EN_REVISION", "Cancelación registrada; falta confirmar la devolución de stock");
    }

    /** No modifica inventario ni inicia/continúa una cancelación. */
    public Resultado consultar(Long id, Long actorId, boolean admin) {
        validarId(id);
        var fila = registro(id, false);
        if (fila != null) {
            autorizar((Long) fila.get("usuario_id"), actorId, admin);
            return resultado(fila);
        }
        var pedidos = jdbc.queryForList("SELECT usuario_id FROM pedidos WHERE id=?", id);
        if (pedidos.isEmpty()) {
            // Puede haberse completado entre ambas lecturas.
            fila = registro(id, false);
            if (fila == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el pedido ni una cancelación registrada");
            autorizar((Long) fila.get("usuario_id"), actorId, admin);
            return resultado(fila);
        }
        Long propietario = (Long) pedidos.getFirst().get("usuario_id");
        autorizar(propietario, actorId, admin);
        return new Resultado(id, propietario, "NO_SOLICITADA", "No hay una cancelación registrada. Continuar solicitará la cancelación de este pedido.");
    }

    public Set<Long> idsEnRevision() {
        return new HashSet<>(jdbc.queryForList("SELECT pedido_id FROM cancelaciones_pedido WHERE estado='EN_REVISION'", Long.class));
    }

    private Map<String, Object> bloquearPedido(Long id) {
        var filas = jdbc.queryForList("SELECT id,usuario_id,estado FROM pedidos WHERE id=? FOR UPDATE", id);
        return filas.isEmpty() ? null : filas.getFirst();
    }

    private Map<String, Object> registro(Long id, boolean bloquear) {
        var filas = jdbc.queryForList("SELECT * FROM cancelaciones_pedido WHERE pedido_id=?" + (bloquear ? " FOR UPDATE" : ""), id);
        return filas.isEmpty() ? null : filas.getFirst();
    }

    private Resultado resultado(Map<String, Object> fila) {
        return new Resultado((Long) fila.get("pedido_id"), (Long) fila.get("usuario_id"),
            (String) fila.get("estado"), (String) fila.get("mensaje"));
    }

    private Resultado enRevision(Map<String, Object> fila, String mensaje) {
        String texto = mensaje == null ? "La devolución requiere revisión del administrador" : mensaje.substring(0, Math.min(300, mensaje.length()));
        jdbc.update("UPDATE cancelaciones_pedido SET mensaje=? WHERE pedido_id=?", texto, fila.get("pedido_id"));
        return new Resultado((Long) fila.get("pedido_id"), (Long) fila.get("usuario_id"), "EN_REVISION", texto);
    }

    private void autorizar(Long propietario, Long actor, boolean admin) {
        if (actor == null || actor <= 0 || (!admin && !propietario.equals(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido inválido");
    }

    private void limitarEspera() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '15s'");
    }
}
