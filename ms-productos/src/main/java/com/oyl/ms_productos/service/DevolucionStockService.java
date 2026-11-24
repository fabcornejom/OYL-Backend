package com.oyl.ms_productos.service;

import com.oyl.ms_productos.model.Producto;
import com.oyl.ms_productos.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

/** Devuelve todas las líneas o ninguna; una operación aplicada nunca se repite. */
@Service
public class DevolucionStockService {
    public record Item(Long productoId, Integer cantidad) {}
    public record Solicitud(String operacion, List<Item> items) {}
    public record Resultado(String operacion, String estado, String mensaje) {}
    private final JdbcTemplate jdbc;
    private final ProductoRepository productos;
    private final TransactionTemplate tx;

    public DevolucionStockService(JdbcTemplate jdbc, ProductoRepository productos,
                                  PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.productos = productos;
        tx = new TransactionTemplate(gestor);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(20);
    }

    public Resultado aplicar(Solicitud solicitud) {
        if (solicitud == null || solicitud.operacion() == null ||
            !solicitud.operacion().matches("devolucion:pedido:[1-9][0-9]{0,18}") ||
            solicitud.items() == null || solicitud.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Devolución inválida");
        }
        var items = new TreeMap<Long, Integer>();
        for (Item item : solicitud.items()) {
            if (item == null || item.productoId() == null || item.productoId() <= 0 ||
                item.cantidad() == null || item.cantidad() <= 0 ||
                items.put(item.productoId(), item.cantidad()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidades de devolución inválidas");
            }
        }
        String contenido = items.entrySet().stream()
            .map(i -> i.getKey() + ":" + i.getValue()).collect(Collectors.joining(";"));
        tx.executeWithoutResult(t -> {
            limitarEspera();
            jdbc.update("INSERT INTO devoluciones_stock(operacion,contenido,estado) VALUES (?,?,'ABIERTA') ON CONFLICT (operacion) DO NOTHING",
                solicitud.operacion(), contenido);
        });
        return tx.execute(t -> {
            limitarEspera();
            var fila = jdbc.queryForMap("SELECT * FROM devoluciones_stock WHERE operacion=? FOR UPDATE", solicitud.operacion());
            if (!contenido.equals(fila.get("contenido"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La devolución ya identifica otras cantidades");
            }
            if ("APLICADA".equals(fila.get("estado"))) {
                return new Resultado(solicitud.operacion(), "APLICADA", "Las existencias ya fueron devueltas");
            }
            var fichas = new ArrayList<Producto>();
            // Mismo orden que las compras y misma fila que los ajustes administrativos.
            for (var item : items.entrySet()) {
                // La devolución debe encontrar también productos eliminados del catálogo.
                Producto producto = productos.buscarPorIdConBloqueo(item.getKey()).orElse(null);
                if (producto == null) {
                    return pendiente(solicitud.operacion(), "No se devolvió stock: falta el producto " + item.getKey() + ". Requiere revisión del administrador.");
                }
                if (producto.getStock() == null || producto.getStock() < 0 ||
                    (long) producto.getStock() + item.getValue() > Integer.MAX_VALUE) {
                    return pendiente(solicitud.operacion(), "No se devolvió stock: las existencias del producto " + item.getKey() + " requieren revisión.");
                }
                fichas.add(producto);
            }
            // Un producto desactivado igualmente admite devolver lo que se compró.
            for (Producto producto : fichas) {
                producto.setStock(producto.getStock() + items.get(producto.getId()));
            }
            productos.flush();
            jdbc.update("UPDATE devoluciones_stock SET estado='APLICADA',mensaje=NULL WHERE operacion=?", solicitud.operacion());
            return new Resultado(solicitud.operacion(), "APLICADA", "Existencias devueltas");
        });
    }

    private Resultado pendiente(String operacion, String mensaje) {
        // Sigue abierta: tras corregir el impedimento se continúa la misma devolución.
        jdbc.update("UPDATE devoluciones_stock SET mensaje=? WHERE operacion=?", mensaje, operacion);
        return new Resultado(operacion, "PENDIENTE", mensaje);
    }

    private void limitarEspera() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '15s'");
    }
}
