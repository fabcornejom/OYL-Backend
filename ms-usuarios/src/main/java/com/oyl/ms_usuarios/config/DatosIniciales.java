package com.oyl.ms_usuarios.config;

import com.oyl.ms_usuarios.service.UsuarioService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Se ejecuta UNA vez, justo después de que el microservicio arranca
@Component
public class DatosIniciales implements CommandLineRunner {

    private final UsuarioService service;

    public DatosIniciales(UsuarioService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        service.crearAdminSiNoExiste();
        System.out.println(">>> Cuenta admin verificada");
    }
}