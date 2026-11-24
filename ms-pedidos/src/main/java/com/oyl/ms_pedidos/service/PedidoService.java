package com.oyl.ms_pedidos.service;

import com.oyl.ms_pedidos.model.EstadoPedido;
import com.oyl.ms_pedidos.model.Pedido;
import com.oyl.ms_pedidos.repository.PedidoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class PedidoService {
    private final PedidoRepository repo;
    private final JdbcTemplate jdbc;
    private final EntityManager entidades;
    public PedidoService(PedidoRepository repo, JdbcTemplate jdbc, EntityManager entidades) {
        this.repo = repo; this.jdbc = jdbc; this.entidades = entidades;
    }

    public List<Pedido> listar(EstadoPedido estado) {
        return estado == null ? repo.findAllByOrderByFechaDesc() : repo.findByEstadoOrderByFechaDesc(estado);
    }

    public List<Pedido> listarPorUsuario(Long usuarioId) {
        return repo.findByUsuarioIdOrderByFechaDesc(usuarioId);
    }

    public Pedido buscarPorId(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El pedido " + id + " no existe"));
    }

    @Transactional(timeout = 20)
    public Pedido cambiarEstado(Long id, EstadoPedido nuevo) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '15s'");
        var filas = jdbc.queryForList("SELECT estado FROM pedidos WHERE id=? FOR UPDATE", id);
        if (filas.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El pedido no existe");
        Boolean cancelacion = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cancelaciones_pedido WHERE pedido_id=?)", Boolean.class, id);
        if (Boolean.TRUE.equals(cancelacion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido tiene una cancelación en revisión. Debe resolverse antes de cambiar su estado.");
        }
        EstadoPedido actual = EstadoPedido.valueOf((String) filas.getFirst().get("estado"));
        if (nuevo == null || nuevo.ordinal() != actual.ordinal() + 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede pasar de " + actual + " a " + nuevo);
        }
        Pedido pedido = buscarPorId(id);
        // Evitar que una entidad leída anteriormente conserve un estado obsoleto.
        entidades.refresh(pedido);
        pedido.setEstado(nuevo);
        return repo.saveAndFlush(pedido);
    }
}
