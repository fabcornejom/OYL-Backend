package com.oyl.ms_pedidos.model;

import jakarta.persistence.*;

@Entity
@Table(name = "detalles_pedido")
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Muchos detalles pertenecen a un pedido → columna pedido_id
    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Column(nullable = false)
    private Long productoId;          // id del producto en ms-productos

    @Column(nullable = false)
    private String nombreProducto;    // Copia del nombre al momento de comprar

    @Column(nullable = false)
    private Integer precioUnitario;   // Copia del precio al momento de comprar

    @Column(nullable = false)
    private Integer cantidad;

    public DetallePedido() {}

    public Long getSubtotal() {
        return precioUnitario.longValue() * cantidad;
    }

    // ---------- Getters y Setters ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public Integer getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(Integer precioUnitario) { this.precioUnitario = precioUnitario; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
}