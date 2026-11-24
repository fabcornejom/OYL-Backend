package com.oyl.ms_productos.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@Service
public class FotoProductoService {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public record Preparada(byte[] contenido) {}
    public record Guardada(byte[] contenido, String version) {}
    private final JdbcTemplate jdbc;

    public FotoProductoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Validación/normalización antes de abrir la transacción que modifica el producto. */
    public Preparada preparar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) throw invalida("Selecciona una fotografía JPG o PNG");
        if (archivo.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La fotografía procesada supera los 2 MB");
        try {
            byte[] bytes;
            try (var entrada = archivo.getInputStream()) {
                bytes = entrada.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La fotografía procesada supera los 2 MB");
            try (var entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var lectores = ImageIO.getImageReaders(entrada);
                if (!lectores.hasNext()) throw invalida("La fotografía no es una imagen JPG o PNG válida");
                ImageReader lector = lectores.next();
                BufferedImage original = null;
                BufferedImage salida = null;
                try {
                    String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                    if (!Set.of("jpeg", "jpg", "png").contains(formato)) throw invalida("Selecciona una fotografía JPG o PNG");
                    lector.setInput(entrada, true, true);
                    int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                    if (ancho <= 0 || alto <= 0 || ancho > 4096 || alto > 4096) {
                        throw invalida("La fotografía procesada tiene dimensiones inválidas");
                    }
                    original = lector.read(0);
                    if (original == null) throw invalida("No se pudo leer la fotografía");
                    double escala = Math.min(1.0, 1024.0 / Math.max(ancho, alto));
                    salida = new BufferedImage(Math.max(1, (int) (ancho * escala)),
                        Math.max(1, (int) (alto * escala)), BufferedImage.TYPE_INT_RGB);
                    var dibujo = salida.createGraphics();
                    try {
                        dibujo.setColor(Color.WHITE);
                        dibujo.fillRect(0, 0, salida.getWidth(), salida.getHeight());
                        dibujo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        dibujo.drawImage(original, 0, 0, salida.getWidth(), salida.getHeight(), null);
                    } finally { dibujo.dispose(); }
                    var buffer = new ByteArrayOutputStream();
                    var escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
                    try (var destino = new MemoryCacheImageOutputStream(buffer)) {
                        escritor.setOutput(destino);
                        var parametros = escritor.getDefaultWriteParam();
                        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        parametros.setCompressionQuality(0.85f);
                        // Sin metadatos originales (EXIF, ubicación ni nombre del archivo).
                        escritor.write(null, new IIOImage(salida, null, null), parametros);
                        destino.flush();
                    } finally { escritor.dispose(); }
                    byte[] jpeg = buffer.toByteArray();
                    if (jpeg.length == 0 || jpeg.length > MAX_BYTES) throw invalida("La fotografía procesada supera los 2 MB");
                    return new Preparada(jpeg);
                } finally {
                    lector.dispose();
                    if (original != null) original.flush();
                    if (salida != null) salida.flush();
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw invalida("No se pudo leer la fotografía. Selecciona una imagen JPG o PNG válida");
        }
    }

    /** Participa en la transacción del producto; no debe confirmarse por separado. */
    public String guardar(Long productoId, Preparada foto) {
        String version = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO fotos_producto(producto_id,version,contenido) VALUES (?,?,?) ON CONFLICT (producto_id) DO UPDATE SET version=EXCLUDED.version,contenido=EXCLUDED.contenido",
            productoId, version, foto.contenido());
        return "/api/productos/" + productoId + "/foto/" + version;
    }

    public Guardada obtener(Long productoId, String version) {
        if (productoId == null || productoId <= 0 || version == null || !version.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fotografía no encontrada");
        }
        var fotos = jdbc.query("SELECT contenido,version FROM fotos_producto WHERE producto_id=? AND version=?",
            (fila, numero) -> new Guardada(fila.getBytes(1), fila.getString(2)), productoId, version);
        if (fotos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La fotografía ya no está disponible. Actualiza el catálogo");
        return fotos.getFirst();
    }

    public void quitar(Long productoId) {
        jdbc.update("DELETE FROM fotos_producto WHERE producto_id=?", productoId);
    }

    public void validarReferencia(String nueva, String anterior) {
        if (Objects.equals(nueva, anterior)) return; // También conserva referencias locales antiguas sin modificarlas.
        if (nueva == null || nueva.isBlank()) return;
        if (nueva.startsWith("/api/productos/")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La fotografía del producto cambió. Actualiza el catálogo antes de guardar");
        }
        if (nueva.matches("drawable:[a-z][a-z0-9_]*")) return;
        try {
            var uri = java.net.URI.create(nueva);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null && uri.getUserInfo() == null && nueva.length() <= 255) return;
        } catch (IllegalArgumentException ignorada) { }
        throw invalida("La fotografía seleccionada debe subirse al servidor antes de guardar el producto");
    }

    private ResponseStatusException invalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
