package cr.ac.una.operatorsystem.client;

/**
 * Respuesta DENIED;motivo;mensaje de CoreServer: credenciales, sesión o
 * permisos. Es distinta de un ERROR de negocio y de una falla de red.
 */
public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reason;

    public AccessDeniedException(String reason, String message) {
        super(message);
        this.reason = reason == null ? "" : reason;
    }

    public String getReason() {
        return reason;
    }

    /** La sesión ya no sirve: hay que volver a la pantalla de acceso. */
    public boolean endsSession() {
        return "SESSION_EXPIRED".equals(reason)
                || "UNAUTHENTICATED".equals(reason)
                || "ACCOUNT_DISABLED".equals(reason);
    }
}
