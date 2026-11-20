package com.oyl.ms_usuarios.service;

import com.oyl.ms_usuarios.model.Rol;
import com.oyl.ms_usuarios.model.Usuario;
import com.oyl.ms_usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository repo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UsuarioService(UsuarioRepository repo) {
        this.repo = repo;
    }

    // ---------- REGISTRO (solo clientes) ----------
    public Usuario registrar(Usuario usuario) {
        if (repo.existsByAliasIgnoreCase(usuario.getAlias())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El alias " + usuario.getAlias() + " ya está en uso");
        }
        if (repo.existsByCorreoIgnoreCase(usuario.getCorreo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El correo " + usuario.getCorreo() + " ya está registrado");
        }
        usuario.setId(null);
        usuario.setRol(Rol.CLIENTE);   // Nadie puede registrarse como ADMIN
        usuario.setContrasena(encoder.encode(usuario.getContrasena()));   // Encriptar
        return repo.save(usuario);
    }

    // ---------- LOGIN ----------
    public Usuario login(String alias, String contrasena) {
        Usuario usuario = repo.findByAliasIgnoreCase(alias).orElse(null);

        // Mismo mensaje si falla el alias o la contraseña (no damos pistas)
        if (usuario == null || !encoder.matches(contrasena, usuario.getContrasena())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Alias o contraseña incorrectos");
        }
        return usuario;
    }

    // ---------- LEER ----------
    public List<Usuario> listar() {
        return repo.findAll();
    }

    public Usuario buscarPorId(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El usuario " + id + " no existe"));
    }

    // ---------- ACTUALIZAR PERFIL ----------
    // No cambia alias, contraseña ni rol: solo datos de contacto
    public Usuario actualizarPerfil(Long id, String nombre, String correo,
                                    String telefono, String direccion) {
        Usuario usuario = buscarPorId(id);

        if (!usuario.getCorreo().equalsIgnoreCase(correo)
                && repo.existsByCorreoIgnoreCase(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El correo " + correo + " ya está registrado");
        }
        usuario.setNombre(nombre);
        usuario.setCorreo(correo);
        usuario.setTelefono(telefono);
        usuario.setDireccion(direccion);
        return repo.save(usuario);
    }

    // ---------- ELIMINAR ----------
    public void eliminar(Long id) {
        Usuario usuario = buscarPorId(id);
        if (usuario.getRol() == Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar la cuenta de administrador");
        }
        repo.delete(usuario);
    }

    // ---------- CUENTA ADMIN ----------
    public void crearAdminSiNoExiste() {
        if (repo.existsByAliasIgnoreCase("admin")) return;

        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setAlias("admin");
        admin.setCorreo("admin@admin.cl");
        admin.setTelefono("+56 9 1234 1234");
        admin.setDireccion("Santiago");
        admin.setContrasena(encoder.encode("admin"));   // Igual que en la hoja de ruta: admin/admin
        admin.setRol(Rol.ADMIN);
        repo.save(admin);
    }
}