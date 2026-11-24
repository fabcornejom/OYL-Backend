package com.oyl.ms_usuarios.security;

import com.oyl.ms_usuarios.dto.LoginResponse;
import com.oyl.ms_usuarios.dto.UsuarioResponse;
import com.oyl.ms_usuarios.model.Usuario;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class EmisorToken {
    private final JwtEncoder encoder;
    public EmisorToken(RSAPublicKey publica,
        @Value("${oyl.security.private-key:file:../.oyl-secrets/jwt-private.pem}") Resource archivo) throws Exception {
        String pem;
        try (var entrada = archivo.getInputStream()) { pem = new String(entrada.readAllBytes(), StandardCharsets.US_ASCII); }
        byte[] der = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
        RSAPrivateKey privada = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        var jwk = new RSAKey.Builder(publica).privateKey(privada).keyID("oyl-academico").build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }
    public LoginResponse emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant vence = ahora.plusSeconds(3600);
        var claims = JwtClaimsSet.builder().issuer(ConfiguracionSeguridad.EMISOR)
            .audience(List.of(ConfiguracionSeguridad.AUDIENCIA)).subject(usuario.getId().toString())
            .issuedAt(ahora).notBefore(ahora).expiresAt(vence).id(UUID.randomUUID().toString())
            .claim("role", usuario.getRol().name()).build();
        var cabecera = JwsHeader.with(SignatureAlgorithm.RS256).keyId("oyl-academico").build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new LoginResponse(UsuarioResponse.desde(usuario), token, "Bearer", vence.getEpochSecond());
    }
}
