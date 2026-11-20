package com.oyl.ms_usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Lo que manda la app al editar el perfil (sin alias, contraseña ni rol)
public record ActualizarPerfilRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido") String correo,
        @NotBlank(message = "El teléfono es obligatorio") String telefono,
        String direccion
) {}