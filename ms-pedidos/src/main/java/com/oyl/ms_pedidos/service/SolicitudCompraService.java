package com.oyl.ms_pedidos.service;
import com.oyl.ms_pedidos.client.ProductoClient;
import com.oyl.ms_pedidos.dto.request.CrearPedidoRequest;
import com.oyl.ms_pedidos.dto.response.PedidoResponse;
import com.oyl.ms_pedidos.model.*;
import com.oyl.ms_pedidos.repository.PedidoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.*;

@Service
public class SolicitudCompraService {
    public record Resultado(String estado,String mensaje,PedidoResponse pedido) {}
    private final JdbcTemplate jdbc;
    private final ProductoClient productos;
    private final PedidoRepository pedidos;
    private final TransactionTemplate tx;
    public SolicitudCompraService(JdbcTemplate jdbc,ProductoClient productos,PedidoRepository pedidos,PlatformTransactionManager gestor) {
        this.jdbc=jdbc;this.productos=productos;this.pedidos=pedidos;
        tx=new TransactionTemplate(gestor);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
    }
    private String clave(Long usuario,String id) {
        if(usuario==null || usuario<=0 || id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere una clave de compra UUID válida");
        return usuario+":"+id;
    }
    public Resultado crear(String id,CrearPedidoRequest req) {
        String clave=clave(req.usuarioId(),id);
        if(req.items()==null || req.items().isEmpty() || req.items().size()>200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Carrito inválido");
        var items=new TreeMap<Long,ProductoClient.ItemCompra>();
        for(var i:req.items()) {
            if(i==null || i.productoId()==null || i.productoId()<=0 || i.cantidad()==null || i.cantidad()<=0 || i.precioEsperado()==null || i.precioEsperado()<=0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Línea de compra inválida");
            var anterior=items.get(i.productoId());
            long cantidad=(long)i.cantidad()+(anterior==null?0:anterior.cantidad());
            if(cantidad>Integer.MAX_VALUE || (anterior!=null && !anterior.precioEsperado().equals(i.precioEsperado())))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cantidad o precio contradictorios");
            items.put(i.productoId(),new ProductoClient.ItemCompra(i.productoId(),(int)cantidad,i.precioEsperado()));
        }
        String contenido=items.values().stream().map(i->i.productoId()+":"+i.cantidad()+":"+i.precioEsperado()).collect(java.util.stream.Collectors.joining(";"));
        tx.executeWithoutResult(t->{
            limitarEspera();
            jdbc.update("INSERT INTO solicitudes_compra(clave,usuario_id,contenido,estado) VALUES (?,?,?,'EN_REVISION') ON CONFLICT (clave) DO NOTHING",clave,req.usuarioId(),contenido);
        });
        // El registro anterior sobrevive a un rollback/caída posterior. No compensar a ciegas.
        return tx.execute(t->{
            limitarEspera();
            var fila=jdbc.queryForMap("SELECT * FROM solicitudes_compra WHERE clave=? FOR UPDATE",clave);
            if(!contenido.equals(fila.get("contenido")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"La clave ya identifica otra compra. Recupera el intento original.");
            if(!"EN_REVISION".equals(fila.get("estado"))) return resultado(fila);
            var stock=productos.comprar("compra:"+clave,new ArrayList<>(items.values()));
            if(stock==null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Inventario no devolvió un comprobante");
            if("RECHAZADA".equals(stock.estado())) {
                jdbc.update("UPDATE solicitudes_compra SET estado='RECHAZADA',mensaje=? WHERE clave=?",stock.mensaje(),clave);
                return new Resultado("RECHAZADA",stock.mensaje(),null);
            }
            if(!"APLICADA".equals(stock.estado()) || stock.detalles()==null || stock.detalles().size()!=items.size() || stock.total()==null || stock.total()<=0 || stock.total()>Integer.MAX_VALUE)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Comprobante de inventario inválido");
            Pedido pedido=new Pedido(); pedido.setUsuarioId(req.usuarioId());
            pedido.setFecha(LocalDateTime.now(ZoneOffset.UTC)); pedido.setEstado(EstadoPedido.PENDIENTE_VALIDACION);
            long total=0;var vistos=new HashSet<Long>();
            for(var d:stock.detalles()) {
                var esperado=items.get(d.productoId());
                if(esperado==null || !vistos.add(d.productoId()) || !esperado.cantidad().equals(d.cantidad()) || !esperado.precioEsperado().equals(d.precio()) || d.nombre()==null)
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Detalle de inventario inválido");
                total+=(long)d.precio()*d.cantidad();
                DetallePedido detalle=new DetallePedido();detalle.setProductoId(d.productoId());detalle.setNombreProducto(d.nombre());
                detalle.setPrecioUnitario(d.precio());detalle.setCantidad(d.cantidad());pedido.agregarDetalle(detalle);
            }
            if(total!=stock.total()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Total de inventario inválido");
            pedido.setTotal(total);pedidos.saveAndFlush(pedido);
            jdbc.update("UPDATE solicitudes_compra SET estado='COMPLETADA',pedido_id=? WHERE clave=?",pedido.getId(),clave);
            return new Resultado("COMPLETADA",null,PedidoResponse.desde(pedido));
        });
    }
    /** Solo consulta: nunca descuenta, crea ni continúa automáticamente. */
    public Resultado consultar(Long usuario,String id) {
        var filas=jdbc.queryForList("SELECT * FROM solicitudes_compra WHERE clave=?",clave(usuario,id));
        if(filas.isEmpty()) return new Resultado("NO_RECIBIDA","El servidor no recibió todavía esta solicitud. Puedes continuar el mismo intento.",null);
        return resultado(filas.getFirst());
    }
    private Resultado resultado(Map<String,Object> fila) {
        Long pedidoId=(Long)fila.get("pedido_id");
        PedidoResponse pedido=pedidoId==null?null:pedidos.findById(pedidoId).map(PedidoResponse::desde).orElse(null);
        String estado=(String)fila.get("estado");
        if("COMPLETADA".equals(estado) && pedido==null)
            return new Resultado("FINALIZADA_SIN_PEDIDO","La compra fue registrada como pedido "+pedidoId+", pero ese pedido ya no está disponible. No se reenviará.",null);
        return new Resultado(estado,(String)fila.get("mensaje"),pedido);
    }
    private void limitarEspera() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '15s'");
    }

}
