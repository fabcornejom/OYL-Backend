package com.oyl.ms_productos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class FiltroStockInterno extends OncePerRequestFilter {
    private final byte[] clave;
    public FiltroStockInterno(@Value("${oyl.security.stock-token:file:../.oyl-secrets/stock-service.token}") Resource archivo) throws IOException {
        try (var entrada = archivo.getInputStream()) { clave = new String(entrada.readAllBytes(), StandardCharsets.US_ASCII).trim().getBytes(StandardCharsets.US_ASCII); }
        if (clave.length < 43) throw new IllegalStateException("Prepara la credencial interna de inventario antes de iniciar el servicio");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena) throws ServletException, IOException {
        String enviada = solicitud.getHeader("X-Oyl-Service-Token");
        if (enviada != null) {
            String ruta = solicitud.getRequestURI();
            boolean alcance = (solicitud.getMethod().equals("GET") && ruta.matches("/api/productos/[1-9][0-9]*"))
                || (solicitud.getMethod().equals("POST") && (ruta.equals("/api/productos/compras-stock") || ruta.equals("/api/productos/devoluciones-stock")))
                || (solicitud.getMethod().equals("PATCH") && ruta.matches("/api/productos/[1-9][0-9]*/stock"));
            if (!MessageDigest.isEqual(clave, enviada.getBytes(StandardCharsets.US_ASCII))) {
                respuesta.sendError(401); return;
            }
            if (!alcance) { respuesta.sendError(403); return; }
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(new UsernamePasswordAuthenticationToken("ms-pedidos", null,
                List.of(new SimpleGrantedAuthority("ROLE_STOCK_SERVICE"))));
            SecurityContextHolder.setContext(contexto);
        }
        cadena.doFilter(solicitud, respuesta);
    }
}
