package com.oyl.ms_usuarios.dto;

import com.oyl.ms_usuarios.model.Rol;
import com.oyl.ms_usuarios.model.Usuario;

// Lo que el backend DEVUELVE: todo menos la contraseña
public record UsuarioResponse(
        Long id,
        String nombre,
        String alias,
        String correo,
        String telefono,
        String direccion,
        Rol rol
) {
    // Convierte un Usuario (tabla) en la respuesta
    public static UsuarioResponse desde(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getAlias(),
                u.getCorreo(), u.getTelefono(), u.getDireccion(), u.getRol());
    }
}