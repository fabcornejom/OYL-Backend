package com.oyl.ms_usuarios.dto;

/** La credencial solo se devuelve al autenticar; las respuestas de perfil no contienen tokens. */
public record LoginResponse(UsuarioResponse usuario, String accessToken, String tokenType, long expiresAtEpochSeconds) {}
