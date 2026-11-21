package com.oyl.ms_productos.config;

import com.oyl.ms_productos.model.Producto;
import com.oyl.ms_productos.repository.ProductoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Se ejecuta una vez al arrancar el microservicio.
// Si la tabla está vacía, crea el catálogo inicial (ids 1 al 5, en este orden).
@Component
public class DatosIniciales implements CommandLineRunner {

    private final ProductoRepository repository;

    public DatosIniciales(ProductoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // ya hay productos: no tocar nada
        }

        repository.save(crear("Almendras", "OYL001", "Frutos secos",
                "Semillas comestibles del almendro, originarias de Asia. Son crujientes, de sabor suave y ricas en proteínas, fibra y grasas saludables.",
                "Bolsa de 500 g", 6990, 60, "drawable:almendras"));

        repository.save(crear("Nueces", "OYL002", "Frutos secos",
                "Son frutos del nogal, originarios de Asia. Se caracterizan por su textura crujiente y son ricas en proteínas, fibra y grasas saludables.",
                "Bolsa de 500 g", 5990, 60, "drawable:nueces"));

        repository.save(crear("Pasas", "OYL003", "Deshidratados",
                "Son uvas deshidratadas, originarias de Asia. Se caracterizan por su sabor dulce y textura suave. Son ricas en fibra, antioxidantes y minerales.",
                "Bolsa de 500 g", 6000, 30, "drawable:pasas"));

        repository.save(crear("Mix de frutos secos", "OYL004", "Frutos secos",
                "Mezcla de frutos secos que combina nueces y almendras. Tiene una textura crujiente y aporta proteínas, fibra y grasas saludables.",
                "Bolsa de 1 kg", 16000, 30, "drawable:mix"));

        repository.save(crear("Semillas de girasol", "OYL005", "Semillas",
                "Son semillas comestibles provenientes de la flor del girasol. Tienen un sabor suave, una textura crujiente y son ricas en proteínas, fibra y grasas saludables.",
                "Bolsa de 500 g", 7990, 60, "drawable:semillas_girasol"));

        System.out.println(">>> Catálogo inicial creado (5 productos)");
    }

    // Arma un producto con los datos que cambian; stockMinimo=2 y disponible=true son comunes
    private Producto crear(String nombre, String codigo, String categoria, String descripcion,
                           String presentacion, int precio, int stock, String imagenUrl) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setCodigo(codigo);
        p.setCategoria(categoria);
        p.setDescripcion(descripcion);
        p.setPresentacion(presentacion);
        p.setPrecio(precio);
        p.setStock(stock);
        p.setStockMinimo(2);
        p.setDisponible(true);
        p.setImagenUrl(imagenUrl);
        return p;
    }
}