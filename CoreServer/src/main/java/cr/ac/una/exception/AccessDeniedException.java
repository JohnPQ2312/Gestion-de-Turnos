package cr.ac.una.exception;

import java.util.Objects;

// Representa rechazos de credenciales, sesión o permisos.
// El protocolo los devuelve como DENIED para distinguirlos de los errores de negocio.
public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        // Usuario o contraseña incorrectos, o funcionario sin credencial.
        INVALID_CREDENTIALS,
        // Funcionario inactivo o perfil sin acceso.
        ACCOUNT_DISABLED,
        // El comando necesita sesión y no se envió.
        UNAUTHENTICATED,
        // Token desconocido, vencido o cerrado.
        SESSION_EXPIRED,
        // La sesión es válida, pero no tiene permiso para esa operación.
        FORBIDDEN
    }

    private final Reason reason;

    public AccessDeniedException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "El motivo es obligatorio.");
    }

    public Reason getReason() {
        return reason;
    }
}
