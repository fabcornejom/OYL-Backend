package com.oyl.ms_usuarios.repository;

import com.oyl.ms_usuarios.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByAliasIgnoreCase(String alias);   // Para el login

    boolean existsByAliasIgnoreCase(String alias);           // ¿Alias ya usado?

    boolean existsByCorreoIgnoreCase(String correo);         // ¿Correo ya usado?
}