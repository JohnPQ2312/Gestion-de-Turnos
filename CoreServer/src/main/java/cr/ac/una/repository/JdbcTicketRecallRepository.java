package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.RecalledTicket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

// Actualiza el último llamado y su contador dentro de una transacción.
// Conserva el primer llamado, el consecutivo y la asignación del ticket.
public class JdbcTicketRecallRepository implements TicketRecallRepository {
    private final DatabaseConnectionFactory connectionFactory;

    public JdbcTicketRecallRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory,
                "La fábrica de conexiones es obligatoria.");
    }

    @Override
    public RecalledTicket recall(long assignmentId, long ticketId, int operatorId) {
        if (assignmentId <= 0 || ticketId <= 0 || operatorId <= 0) {
            throw new IllegalArgumentException("Los identificadores del rellamado deben ser positivos.");
        }
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                // Mismo orden del llamado: asignación, operador, ventanilla, turno.
                int windowId = lockAssignment(connection, assignmentId, operatorId);
                requireActiveOperator(connection, operatorId);
                requireActiveWindow(connection, windowId);
                RecalledTicket result = recallLockedTicket(connection, assignmentId, ticketId);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No se pudo completar el rellamado en MySQL.", exception);
        }
    }

    private int lockAssignment(Connection connection, long assignmentId, int operatorId)
            throws SQLException {
        String sql = """
                SELECT operator_id, window_id, started_at, ended_at
                FROM operator_window_assignment WHERE assignment_id = ? FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("La asignación solicitada no existe.");
                }
                if (result.getInt("operator_id") != operatorId) {
                    throw new IllegalStateException("La asignación pertenece a otro funcionario.");
                }
                Timestamp startedAt = result.getTimestamp("started_at");
                if (startedAt == null || result.getTimestamp("ended_at") != null
                        || startedAt.toLocalDateTime().isAfter(LocalDateTime.now())) {
                    throw new IllegalStateException("La asignación debe estar abierta y haber iniciado.");
                }
                return result.getInt("window_id");
            }
        }
    }

    private void requireActiveOperator(Connection connection, int operatorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT operator_state FROM `operator` WHERE operator_id = ? FOR UPDATE")) {
            statement.setInt(1, operatorId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"ACTIVO".equals(result.getString(1))) {
                    throw new IllegalStateException("El funcionario no existe o está inactivo.");
                }
            }
        }
    }

    private void requireActiveWindow(Connection connection, int windowId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT window_state FROM `window` WHERE window_id = ? FOR UPDATE")) {
            statement.setInt(1, windowId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("La ventanilla no existe.");
                }
                if ("PAUSADA".equals(result.getString(1))) {
                    throw new IllegalStateException("Reanude la ventanilla antes de volver a llamar.");
                }
                if (!"ACTIVA".equals(result.getString(1))) {
                    throw new IllegalStateException("La ventanilla está inactiva.");
                }
            }
        }
    }

    // Solo admite un ticket LLAMADO de esta asignación.
    // Actualiza último llamado y contador sin cambiar el primer llamado ni generar otro turno.
    private RecalledTicket recallLockedTicket(Connection connection, long assignmentId, long ticketId)
            throws SQLException {
        String sql = """
                SELECT assignment_id, ticket_state, called_at, last_called_at, call_count
                FROM ticket WHERE ticket_id = ? FOR UPDATE
                """;
        int count;
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("El turno solicitado no existe.");
                }
                long owner = result.getLong("assignment_id");
                if (result.wasNull() || owner != assignmentId) {
                    throw new IllegalStateException("El turno no pertenece a la asignación seleccionada.");
                }
                if (!"LLAMADO".equals(result.getString("ticket_state"))) {
                    throw new IllegalStateException("Solo se puede volver a llamar un turno en estado LLAMADO.");
                }
                Timestamp first = result.getTimestamp("called_at");
                Timestamp last = result.getTimestamp("last_called_at");
                long previousCount = result.getLong("call_count");
                if (first == null || last == null || previousCount < 1
                        || last.before(first)) {
                    throw new SQLException("El historial de llamados del turno es inválido.");
                }
                if (previousCount >= Integer.MAX_VALUE) {
                    throw new IllegalStateException("El contador de llamados alcanzó su límite.");
                }
                if (now.isBefore(last.toLocalDateTime())) {
                    throw new IllegalStateException("El último llamado es posterior al reloj actual. Revise la hora del servidor.");
                }
                count = (int) previousCount + 1;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE ticket SET last_called_at = ?, call_count = ?
                WHERE ticket_id = ? AND assignment_id = ? AND ticket_state = 'LLAMADO'
                """)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setInt(2, count);
            statement.setLong(3, ticketId);
            statement.setLong(4, assignmentId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se actualizó el turno esperado.");
            }
        }
        return new RecalledTicket(assignmentId, ticketId, count, now);
    }
}
