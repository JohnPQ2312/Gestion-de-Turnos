package cr.ac.una.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256 (incluido en el JDK).
 *
 * Parámetros según la guía de OWASP para PBKDF2-HMAC-SHA256: 600 000
 * iteraciones, sal aleatoria de 16 bytes y clave derivada de 256 bits.
 * Formato guardado: pbkdf2_sha256$<iteraciones>$<sal Base64>$<hash Base64>.
 * Las iteraciones quedan en cada hash, así que pueden aumentarse en el
 * futuro sin invalidar las contraseñas ya guardadas.
 */
public final class PasswordHasher {

    public static final String ALGORITHM_ID = "pbkdf2_sha256";
    public static final int ITERATIONS = 600_000;
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private static final String JCA_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int MAX_ACCEPTED_ITERATIONS = 10_000_000;
    // Sal fija solo para gastar el mismo tiempo cuando el usuario no existe.
    private static final byte[] DUMMY_SALT = "turnos-dummy-salt".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom random = new SecureRandom();

    /** Valida la contraseña nueva y devuelve el texto a guardar. */
    public String hash(char[] password) {
        validateNewPassword(password);

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);

        Base64.Encoder encoder = Base64.getEncoder();
        return ALGORITHM_ID + "$" + ITERATIONS + "$"
                + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    /** Compara en tiempo constante. Un hash con formato inválido nunca coincide. */
    public boolean verify(char[] password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }

        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 4 || !ALGORITHM_ID.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > MAX_ACCEPTED_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Mismo costo que verify, para no revelar si un usuario existe. */
    public void verifyDummy(char[] password) {
        derive(password == null ? new char[0] : password, DUMMY_SALT, ITERATIONS);
    }

    public static void validateNewPassword(char[] password) {
        if (password == null || password.length < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres."
            );
        }
        if (password.length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña no puede superar " + MAX_LENGTH + " caracteres."
            );
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(JCA_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("No se pudo calcular el hash de la contraseña.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    /** Borra una copia de la contraseña que ya no se necesita. */
    public static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
