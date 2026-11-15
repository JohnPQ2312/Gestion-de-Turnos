package cr.ac.una.service;

// Regla única del nombre de acceso para altas, ediciones e inicio de sesión.
// Se normaliza quitando los espacios exteriores (String.trim) y se mide en puntos de código
// Unicode, igual que la columna VARCHAR(50) de utf8mb4. Así un mismo nombre es aceptado o
// rechazado de la misma forma en todos los puntos de entrada.
// Esta clase no impone otras restricciones al inicio de sesión: los controles y espacios
// interiores solo se rechazan al crear o editar, para no bloquear cuentas ya existentes.
public final class UsernamePolicy {

    public static final int MAX_LENGTH = 50;

    private UsernamePolicy() {
    }

    // Devuelve el nombre sin espacios exteriores; es el valor que se guarda y se busca.
    public static String normalize(String value) {
        return value.trim();
    }

    // Indica si el nombre ya normalizado cabe en el límite, contado en puntos de código.
    public static boolean fits(String normalized) {
        return normalized.codePointCount(0, normalized.length()) <= MAX_LENGTH;
    }
}
