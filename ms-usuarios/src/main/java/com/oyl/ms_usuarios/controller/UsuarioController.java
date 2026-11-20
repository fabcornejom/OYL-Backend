package com.oyl.ms_usuarios.controller;

import com.oyl.ms_usuarios.dto.ActualizarPerfilRequest;
import com.oyl.ms_usuarios.dto.LoginRequest;
import com.oyl.ms_usuarios.dto.UsuarioResponse;
import com.oyl.ms_usuarios.model.Usuario;
import com.oyl.ms_usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

   // POST /api/usuarios/registro  → crear cuenta 
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody Usuario usuario) {
        return UsuarioResponse.desde(service.registrar(usuario));
    }

    // POST /api/usuarios/login  → iniciar sesión
    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest login) {
        return UsuarioResponse.desde(service.login(login.alias(), login.contrasena()));
    }

    // GET /api/usuarios  → listar todos (para el admin)
    @GetMapping
    public List<UsuarioResponse> listar() {
        return service.listar().stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    // GET /api/usuarios/3  → ver un perfil
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return UsuarioResponse.desde(service.buscarPorId(id));
    }

    // PUT /api/usuarios/3  → editar datos de contacto
    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ActualizarPerfilRequest datos) {
        return UsuarioResponse.desde(service.actualizarPerfil(id, datos.nombre(),
                datos.correo(), datos.telefono(), datos.direccion()));
    }

    // DELETE /api/usuarios/3  → eliminar cuenta
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}