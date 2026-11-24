package com.oyl.ms_productos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {
    public static final String EMISOR = "oyl-usuarios";
    public static final String AUDIENCIA = "oyl-api";

    @Bean
    RSAPublicKey clavePublica(@Value("${oyl.security.public-key:file:../.oyl-secrets/jwt-public.pem}") Resource archivo) throws Exception {
        String pem;
        try (var entrada = archivo.getInputStream()) { pem = new String(entrada.readAllBytes(), StandardCharsets.US_ASCII); }
        byte[] der = Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", ""));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAPublicKey publica) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publica).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> datos = jwt -> {
            boolean valido;
            try {
                valido = Long.parseLong(jwt.getSubject()) > 0 && jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCIA)
                    && Set.of("ADMIN", "CLIENTE").contains(jwt.getClaimAsString("role"))
                    && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt());
            } catch (RuntimeException e) { valido = false; }
            return valido ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Credencial no válida para OYL FS", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(EMISOR),
            new JwtTimestampValidator(Duration.ZERO), datos));
        return decoder;
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<FiltroStockInterno> registroFiltro(FiltroStockInterno filtro) {
        var registro = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, FiltroStockInterno filtroStock) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter autenticacion = new JwtAuthenticationConverter();
        autenticacion.setJwtGrantedAuthoritiesConverter(roles);
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reglas -> reglas
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(recurso -> recurso.jwt(jwt -> jwt.jwtAuthenticationConverter(autenticacion)));
        http.addFilterBefore(filtroStock, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
