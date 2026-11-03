package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import cr.ac.una.model.TicketState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;

public class JdbcTicketLifecycleRepository implements TicketLifecycleRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcTicketLifecycleRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public void startAttention(long assignmentId, long ticketId) {
        changeState(assignmentId, ticketId, true);
    }

    @Override
    public void finishAttention(long assignmentId, long ticketId) {
        changeState(assignmentId, ticketId, false);
    }

    private void changeState(long assignmentId, long ticketId, boolean starting) {
        if (assignmentId <= 0 || ticketId <= 0) {
            throw new IllegalArgumentException(
                    "Los identificadores de la asignación y del turno deben ser positivos."
            );
        }

        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                OperatorWindowAssignment assignment = lockAssignment(connection, assignmentId);
                TicketState expectedState = starting
                        ? TicketState.LLAMADO : TicketState.EN_ATENCION;
                Ticket ticket = lockTicket(connection, ticketId, assignment, expectedState);

                if (starting) {
                    ticket.startAttention();
                } else {
                    ticket.finishAttention();
                }

                saveTransition(connection, ticketId, assignmentId, ticket, starting);
                connection.commit();

            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo completar el cambio de estado del turno en MySQL.",
                    exception
            );
        }
    }

    private OperatorWindowAssignment lockAssignment(
            Connection connection,
            long assignmentId
    ) throws SQLException {
        String sql = """
                SELECT assignment_id, operator_id, window_id, started_at, ended_at
                FROM operator_window_assignment
                WHERE assignment_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("La asignación solicitada no existe.");
                }

                Timestamp startedAt = result.getTimestamp("started_at");
                Timestamp endedAt = result.getTimestamp("ended_at");

                if (startedAt == null) {
                    throw new SQLException("La asignación no tiene fecha de inicio.");
                }

                if (endedAt != null
                        || startedAt.toLocalDateTime().isAfter(LocalDateTime.now())) {
                    throw new IllegalStateException(
                            "La asignación debe estar abierta y haber iniciado."
                    );
                }

                try {
                    return new OperatorWindowAssignment(
                            result.getLong("assignment_id"),
                            result.getInt("operator_id"),
                            result.getInt("window_id"),
                            startedAt.toLocalDateTime(), null
                    );
                } catch (IllegalArgumentException exception) {
                    throw new SQLException("La asignación contiene datos inválidos.", exception);
                }
            }
        }
    }

    private Ticket lockTicket(
            Connection connection,
            long ticketId,
            OperatorWindowAssignment assignment,
            TicketState expectedState
    ) throws SQLException {
        String sql = """
                SELECT service_id, assignment_id, sequential_number, `priority`,
                       ticket_state, created_at, called_at, started_at, ended_at
                FROM ticket
                WHERE ticket_id = ?
                FOR UPDATE
                """;

        String serviceCode;
        int sequentialNumber;
        boolean priority;
        Timestamp createdAt;
        Timestamp calledAt;
        Timestamp startedAt;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("El turno solicitado no existe.");
                }

                long currentAssignmentId = result.getLong("assignment_id");
                if (result.wasNull() || currentAssignmentId != assignment.getAssignmentId()) {
                    throw new IllegalStateException(
                            "El turno no pertenece a la asignación seleccionada."
                    );
                }

                String currentState = result.getString("ticket_state");
                if (!expectedState.name().equals(currentState)) {
                    String action = expectedState == TicketState.LLAMADO ? "iniciar" : "finalizar";
                    throw new IllegalStateException(
                            "No se puede " + action + " la atención del turno. "
                            + "Estado actual: " + currentState + "."
                    );
                }

                serviceCode = result.getString("service_id");
                sequentialNumber = result.getInt("sequential_number");
                priority = result.getBoolean("priority");
                createdAt = result.getTimestamp("created_at");
                calledAt = result.getTimestamp("called_at");
                startedAt = result.getTimestamp("started_at");

                if (createdAt == null || calledAt == null
                        || result.getTimestamp("ended_at") != null) {
                    throw new SQLException("Las fechas guardadas del turno activo son inválidas.");
                }
            }
        }

        Service service = findService(connection, serviceCode);

        try {
            return Ticket.restoreActive(
                    service, sequentialNumber, priority,
                    createdAt.toLocalDateTime(), assignment, expectedState,
                    calledAt.toLocalDateTime(),
                    startedAt == null ? null : startedAt.toLocalDateTime()
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException("El turno guardado contiene datos inválidos.", exception);
        }
    }

    private Service findService(Connection connection, String serviceCode) throws SQLException {
        String sql = """
                SELECT service_code, services_name, service_prefix
                FROM service
                WHERE service_code = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, serviceCode);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No existe el servicio del turno guardado.");
                }

                try {
                    return new Service(
                            result.getString("service_code"),
                            result.getString("services_name"),
                            result.getString("service_prefix")
                    );
                } catch (IllegalArgumentException exception) {
                    throw new SQLException("El servicio contiene datos inválidos.", exception);
                }
            }
        }
    }

    private void saveTransition(
            Connection connection,
            long ticketId,
            long assignmentId,
            Ticket ticket,
            boolean starting
    ) throws SQLException {
        String sql = starting ? """
                UPDATE ticket
                SET ticket_state = 'EN_ATENCION', started_at = ?
                WHERE ticket_id = ? AND assignment_id = ? AND ticket_state = 'LLAMADO'
                """ : """
                UPDATE ticket
                SET ticket_state = 'FINALIZADO', ended_at = ?
                WHERE ticket_id = ? AND assignment_id = ? AND ticket_state = 'EN_ATENCION'
                """;

        LocalDateTime transitionTime = starting ? ticket.getStartedAt() : ticket.getEndedAt();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(transitionTime));
            statement.setLong(2, ticketId);
            statement.setLong(3, assignmentId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se actualizó el turno esperado.");
            }
        }
    }
}
