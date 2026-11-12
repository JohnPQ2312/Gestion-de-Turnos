package cr.ac.una.exception;

import java.util.Objects;

/**
 * Rechazo de acceso: credenciales, sesión o permisos. Se responde como
 * DENIED;<motivo>;<mensaje>, distinto de los ERROR de reglas de negocio.
 */
public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** Usuario o contraseña incorrectos, o funcionario sin credencial. */
        INVALID_CREDENTIALS,
        /** Funcionario inactivo o perfil sin acceso. */
        ACCOUNT_DISABLED,
        /** El comando necesita sesión y no se envió. */
        UNAUTHENTICATED,
        /** Token desconocido, vencido o cerrado. */
        SESSION_EXPIRED,
        /** La sesión es válida, pero no tiene permiso para esa operación. */
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
