package com.oyl.ms_pedidos.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// Se encarga de TODA la comunicación con ms-productos
@Component
public class ProductoClient {

    // Solo los datos del producto que necesita ms-pedidos
    public record ProductoDto(Long id, String nombre, Integer precio,
                              Integer stock, Boolean disponible) {}

    private final RestClient restClient;

    // @Value lee "productos.url" desde application.properties
    public ProductoClient(@Value("${productos.url}") String productosUrl) {
        this.restClient = RestClient.create(productosUrl);
    }

    // GET /api/productos/{id}
    public ProductoDto obtener(Long id) {
        try {
            return restClient.get()
                    .uri("/api/productos/{id}", id)
                    .retrieve()
                    .body(ProductoDto.class);
        } catch (HttpClientErrorException e) {
            throw traducirError(e);
        } catch (ResourceAccessException e) {
            throw noDisponible();
        }
    }

    // PATCH /api/productos/{id}/stock  {"cambio": n}
    public void ajustarStock(Long id, int cambio) {
        try {
            restClient.patch()
                    .uri("/api/productos/{id}/stock", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cambio", cambio))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw traducirError(e);
        } catch (ResourceAccessException e) {
            throw noDisponible();
        }
    }

    // Reenvía el mismo código y mensaje que respondió ms-productos (404, 409...)
    private ResponseStatusException traducirError(HttpClientErrorException e) {
        String mensaje = "Error al consultar ms-productos";
        try {
            Map<?, ?> cuerpo = e.getResponseBodyAs(Map.class);
            if (cuerpo != null && cuerpo.get("message") != null) {
                mensaje = cuerpo.get("message").toString();
            }
        } catch (Exception ignorado) {
            // Si no se puede leer el cuerpo, queda el mensaje genérico
        }
        return new ResponseStatusException(e.getStatusCode(), mensaje);
    }

    // ms-productos está apagado o no responde
    private ResponseStatusException noDisponible() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de productos no está disponible");
    }
}