package cr.ac.una.exception;

// Conserva la causa de un fallo JDBC y un mensaje que las capas superiores pueden informar.
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
