package com.oyl.ms_productos.service;

import com.oyl.ms_productos.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

/** La intención se confirma primero; stock y comprobante se confirman juntos después. */
@Service
public class MovimientoStockService {
    public record Solicitud(Long productoId, Integer cambio) {}
    public record Resultado(Long actorId, UUID clave, Long productoId, Integer cambio, String estado,
                            Integer stockAnterior, Integer stockResultante, String mensaje) {}
    private final JdbcTemplate jdbc;
    private final ProductoRepository productos;
    private final TransactionTemplate tx;
    public MovimientoStockService(JdbcTemplate jdbc, ProductoRepository productos, PlatformTransactionManager gestor) {
        this.jdbc=jdbc; this.productos=productos;
        tx=new TransactionTemplate(gestor);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(20);
    }
    public Resultado aplicar(long actor, UUID clave, Solicitud solicitud) {
        validarActor(actor);
        if (clave==null || solicitud==null || solicitud.productoId()==null || solicitud.productoId()<=0 ||
            solicitud.cambio()==null || solicitud.cambio()==0 || solicitud.cambio()==Integer.MIN_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Movimiento de inventario inválido");
        tx.executeWithoutResult(t -> {
            limitarEspera();
            jdbc.update("INSERT INTO movimientos_stock(actor_id,clave,producto_id,cambio,estado) VALUES (?,?,?,?,'ABIERTA') ON CONFLICT (actor_id,clave) DO NOTHING",
                actor,clave,solicitud.productoId(),solicitud.cambio());
        });
        return tx.execute(t -> {
            limitarEspera();
            var fila=jdbc.queryForMap("SELECT producto_id,cambio FROM movimientos_stock WHERE actor_id=? AND clave=? FOR UPDATE",actor,clave);
            if (!solicitud.productoId().equals(fila.get("producto_id")) || !solicitud.cambio().equals(fila.get("cambio")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"La clave del movimiento ya corresponde a otro producto o cantidad");
            Resultado anterior=consultar(actor,clave);
            if (!"ABIERTA".equals(anterior.estado())) return anterior;
            var producto=productos.buscarPorIdConBloqueo(solicitud.productoId()).orElse(null);
            String error=null;
            if (producto==null || producto.isEliminado()) error="El producto ya no está disponible en el inventario";
            else if (producto.getStock()==null || producto.getStock()<0) error="Las existencias del producto requieren revisión";
            else {
                long nuevo=(long)producto.getStock()+solicitud.cambio();
                if (nuevo<0) error="Stock insuficiente para este movimiento";
                else if (nuevo>Integer.MAX_VALUE) error="El stock supera el máximo permitido";
            }
            if (error!=null) {
                jdbc.update("UPDATE movimientos_stock SET estado='RECHAZADA',mensaje=? WHERE actor_id=? AND clave=?",error,actor,clave);
            } else {
                int stockAnterior=producto.getStock();
                int stockResultante=(int)((long)stockAnterior+solicitud.cambio());
                producto.setStock(stockResultante);
                productos.flush();
                jdbc.update("UPDATE movimientos_stock SET estado='APLICADA',stock_anterior=?,stock_resultante=?,mensaje='Movimiento registrado' WHERE actor_id=? AND clave=?",
                    stockAnterior,stockResultante,actor,clave);
            }
            return consultar(actor,clave);
        });
    }
    // Solo lectura: no registra una intención ni continúa movimientos abiertos.
    public Resultado consultar(long actor, UUID clave) {
        validarActor(actor);
        var filas=jdbc.query("SELECT producto_id,cambio,estado,stock_anterior,stock_resultante,mensaje FROM movimientos_stock WHERE actor_id=? AND clave=?",
            (r,n) -> new Resultado(actor,clave,r.getLong(1),r.getInt(2),r.getString(3),
                r.getObject(4,Integer.class),r.getObject(5,Integer.class),r.getString(6)),actor,clave);
        return filas.isEmpty() ? new Resultado(actor,clave,null,null,"NO_REGISTRADA",null,null,
            "El servidor no tiene este movimiento registrado. Puedes continuar el mismo movimiento.") : filas.getFirst();
    }
    private void validarActor(long actor) {
        if (actor<=0) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Identidad de inventario inválida");
    }
    private void limitarEspera() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '15s'");
    }
}
