package cr.ac.una.model;

import java.util.regex.Pattern;

// Fila del catálogo administrativo de servicios.
// issuedTickets es el último consecutivo; pendingTickets cuenta en espera, llamados y en atención.
// openRoutes cuenta asignaciones abiertas cuyo funcionario y ventanilla tienen habilitado el servicio.
public record ManagedService(String code, String name, String prefix, boolean active,
        long issuedTickets, long pendingTickets, long openRoutes, boolean prefixLocked) {

    // Prefijo nuevo: solo letras ASCII mayúsculas, VARCHAR(5).
    // Sin dígitos: el código visual es prefijo + número y así no hay ambigüedad (A + 12 no coincide con A1 + 2).
    private static final Pattern NEW_PREFIX = Pattern.compile("[A-Z]{1,5}");

    public static boolean isValidNewPrefix(String prefix) {
        return prefix != null && NEW_PREFIX.matcher(prefix).matches();
    }
}
