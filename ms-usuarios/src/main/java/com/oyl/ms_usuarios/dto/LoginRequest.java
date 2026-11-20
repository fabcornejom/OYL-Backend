package com.oyl.ms_usuarios.dto;

import jakarta.validation.constraints.NotBlank;

// Lo que manda la app al iniciar sesión
public record LoginRequest(
        @NotBlank(message = "El alias es obligatorio") String alias,
        @NotBlank(message = "La contraseña es obligatoria") String contrasena
) {}