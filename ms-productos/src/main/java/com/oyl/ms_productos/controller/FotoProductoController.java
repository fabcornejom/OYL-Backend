package com.oyl.ms_productos.controller;

import com.oyl.ms_productos.model.Producto;
import com.oyl.ms_productos.service.FotoProductoService;
import com.oyl.ms_productos.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/productos")
public class FotoProductoController {
    private final ProductoService productos;
    private final FotoProductoService fotos;
    public FotoProductoController(ProductoService productos, FotoProductoService fotos) {
        this.productos = productos; this.fotos = fotos;
    }

    @PostMapping(value = "/con-foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Producto crear(@Valid @RequestPart("producto") Producto producto, @RequestPart("foto") MultipartFile foto) {
        return productos.crearConFoto(producto, fotos.preparar(foto));
    }

    @PutMapping(value = "/{id}/con-foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Producto actualizar(@PathVariable Long id, @Valid @RequestPart("producto") Producto producto, @RequestPart("foto") MultipartFile foto) {
        return productos.actualizarConFoto(id, producto, fotos.preparar(foto));
    }

    @GetMapping("/{id}/foto/{version}")
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public ResponseEntity<byte[]> obtener(@PathVariable Long id, @PathVariable String version) {
        var foto = fotos.obtener(id, version);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
            .contentLength(foto.contenido().length)
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(foto.contenido());
    }
}
