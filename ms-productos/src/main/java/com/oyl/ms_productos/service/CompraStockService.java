package com.oyl.ms_productos.service;

import com.oyl.ms_productos.model.Producto;
import com.oyl.ms_productos.repository.ProductoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Registro durable y descuento completo en una sola transacción de productos. */
@Service
public class CompraStockService {
    public record Item(Long productoId, Integer cantidad, Integer precioEsperado) {}
    public record Solicitud(String operacion, List<Item> items) {}
    public record Linea(Long productoId, String nombre, Integer precio, Integer cantidad) {}
    public record Resultado(String estado, String mensaje, Long total, List<Linea> detalles) {}
    private final JdbcTemplate jdbc;
    private final ProductoRepository productos;
    private final TransactionTemplate tx;
    public CompraStockService(JdbcTemplate jdbc, ProductoRepository productos, PlatformTransactionManager gestor) {
        this.jdbc=jdbc; this.productos=productos;
        tx=new TransactionTemplate(gestor);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(20);
    }
    public Resultado aplicar(Solicitud solicitud) {
        if (solicitud == null || solicitud.operacion() == null ||
            !solicitud.operacion().matches("compra:[1-9][0-9]*:[0-9a-f-]{36}") ||
            solicitud.items() == null || solicitud.items().isEmpty() || solicitud.items().size()>200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Operación de inventario inválida");
        var items=new TreeMap<Long,Item>();
        for (Item i:solicitud.items()) {
            if(i==null || i.productoId()==null || i.productoId()<=0 || i.cantidad()==null || i.cantidad()<=0 ||
               i.precioEsperado()==null || i.precioEsperado()<=0 || items.put(i.productoId(),i)!=null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Líneas de inventario inválidas");
        }
        String contenido=items.values().stream().map(i->i.productoId()+":"+i.cantidad()+":"+i.precioEsperado())
            .collect(java.util.stream.Collectors.joining(";"));
        // Se confirma antes de modificar existencias, incluso con solicitudes concurrentes.
        tx.executeWithoutResult(t->{
            limitarEspera();
            jdbc.update("INSERT INTO compras_stock(operacion,contenido,estado) VALUES (?,?,'ABIERTA') ON CONFLICT (operacion) DO NOTHING",solicitud.operacion(),contenido);
        });
        return tx.execute(t->{
            limitarEspera();
            var fila=jdbc.queryForMap("SELECT * FROM compras_stock WHERE operacion=? FOR UPDATE",solicitud.operacion());
            if(!contenido.equals(fila.get("contenido")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"La operación ya tiene otro contenido");
            if(!"ABIERTA".equals(fila.get("estado"))) return leer(solicitud.operacion());
            // Siempre bloquear productos en orden para evitar ciclos entre compras.
            var fichas=new ArrayList<Producto>();
            long total=0;
            for(Item i:items.values()) {
                Producto p=productos.buscarPorIdConBloqueo(i.productoId()).orElse(null);
                String error=null;
                if(p==null || p.isEliminado()) error="Un producto ya no está disponible en el catálogo";
                else if(!Boolean.TRUE.equals(p.getDisponible())) error=p.getNombre()+" no está disponible";
                else if(!i.precioEsperado().equals(p.getPrecio())) error="Cambió el precio de "+p.getNombre();
                else if(p.getStock()==null || p.getStock()<i.cantidad()) error="Stock insuficiente de "+p.getNombre();
                if(error!=null) return rechazar(solicitud.operacion(),error);
                long subtotal=(long)p.getPrecio()*i.cantidad();
                if(subtotal>Integer.MAX_VALUE-total) return rechazar(solicitud.operacion(),"El total supera el máximo permitido");
                total+=subtotal; fichas.add(p);
            }
            for(Producto p:fichas) {
                Item i=items.get(p.getId());
                p.setStock(p.getStock()-i.cantidad());
                jdbc.update("INSERT INTO compras_stock_detalles(operacion,producto_id,nombre,precio,cantidad) VALUES (?,?,?,?,?)",
                    solicitud.operacion(),p.getId(),p.getNombre(),p.getPrecio(),i.cantidad());
            }
            productos.flush();
            jdbc.update("UPDATE compras_stock SET estado='APLICADA',total=? WHERE operacion=?",total,solicitud.operacion());
            return leer(solicitud.operacion());
        });
    }
    private Resultado rechazar(String id,String mensaje) {
        jdbc.update("UPDATE compras_stock SET estado='RECHAZADA',mensaje=? WHERE operacion=?",mensaje,id);
        return new Resultado("RECHAZADA",mensaje,null,List.of());
    }
    private Resultado leer(String id) {
        var fila=jdbc.queryForMap("SELECT estado,mensaje,total FROM compras_stock WHERE operacion=?",id);
        var detalles=jdbc.query("SELECT producto_id,nombre,precio,cantidad FROM compras_stock_detalles WHERE operacion=? ORDER BY producto_id",
            (r,n)->new Linea(r.getLong(1),r.getString(2),r.getInt(3),r.getInt(4)),id);
        return new Resultado((String)fila.get("estado"),(String)fila.get("mensaje"),(Long)fila.get("total"),detalles);
    }
    private void limitarEspera() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '15s'");
    }

}
