import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/** Ejecutar desde la raíz del backend: java tools/PrepararSeguridad.java */
public class PrepararSeguridad {
    public static void main(String[] args) throws Exception {
        if (!Files.isRegularFile(Path.of("docker-compose.yml"))) throw new IllegalStateException("Ejecuta desde la raíz de OYL-Backend");
        Path carpeta = Path.of(".oyl-secrets");
        Files.createDirectories(carpeta);
        Path privada = carpeta.resolve("jwt-private.pem"), publica = carpeta.resolve("jwt-public.pem"), servicio = carpeta.resolve("stock-service.token");
        long existentes = java.util.stream.Stream.of(privada, publica, servicio).filter(Files::exists).count();
        if (existentes == 3) { System.out.println("La configuración local ya existe; no se reemplazaron claves."); return; }
        if (existentes != 0) throw new IllegalStateException("Configuración incompleta: recupera los archivos existentes antes de continuar");
        var generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(3072);
        var par = generador.generateKeyPair();
        Files.writeString(privada, pem("PRIVATE KEY", par.getPrivate().getEncoded()), StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        Files.writeString(publica, pem("PUBLIC KEY", par.getPublic().getEncoded()), StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        byte[] aleatorio = new byte[32]; new SecureRandom().nextBytes(aleatorio);
        Files.writeString(servicio, Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio), StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        System.out.println("Preparadas claves académicas locales en .oyl-secrets; no deben subirse a Git.");
    }
    private static String pem(String tipo, byte[] datos) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(datos) + "\n-----END " + tipo + "-----\n";
    }
}
