package com.oyl.ms_usuarios.controller;

import com.oyl.ms_usuarios.dto.ActualizarPerfilRequest;
import com.oyl.ms_usuarios.dto.LoginRequest;
import com.oyl.ms_usuarios.dto.UsuarioResponse;
import com.oyl.ms_usuarios.model.Usuario;
import com.oyl.ms_usuarios.service.UsuarioService;
import com.oyl.ms_usuarios.dto.LoginResponse;
import com.oyl.ms_usuarios.security.EmisorToken;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;
    private final EmisorToken tokens;

    public UsuarioController(UsuarioService service, EmisorToken tokens) {
        this.service = service;
        this.tokens = tokens;
    }

   // POST /api/usuarios/registro  → crear cuenta 
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody Usuario usuario) {
        return UsuarioResponse.desde(service.registrar(usuario));
    }

    // POST /api/usuarios/login  → iniciar sesión
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest login) {
        return tokens.emitir(service.login(login.alias(), login.contrasena()));
    }

    // GET /api/usuarios  → listar todos (para el admin)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResponse> listar() {
        return service.listar().stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    // GET /api/usuarios/3  → ver un perfil
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CLIENTE') and #id.toString() == authentication.name)")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return UsuarioResponse.desde(service.buscarPorId(id));
    }

    // PUT /api/usuarios/3  → editar datos de contacto
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CLIENTE') and #id.toString() == authentication.name)")
    public UsuarioResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ActualizarPerfilRequest datos) {
        return UsuarioResponse.desde(service.actualizarPerfil(id, datos.nombre(),
                datos.correo(), datos.telefono(), datos.direccion()));
    }

    // DELETE /api/usuarios/3  → eliminar cuenta
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}