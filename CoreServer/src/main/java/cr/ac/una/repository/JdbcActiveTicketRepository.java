package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.ActiveTicket;
import cr.ac.una.model.TicketState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

// Recupera de MySQL el turno llamado o en atención de una asignación.
public class JdbcActiveTicketRepository implements ActiveTicketRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcActiveTicketRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public Optional<ActiveTicket> findByAssignmentId(long assignmentId) {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la asignación debe ser positivo."
            );
        }

        String sql = """
                SELECT t.ticket_id, t.visual_code, s.services_name,
                       t.assignment_id, a.operator_id, a.window_id, t.ticket_state
                FROM ticket t
                JOIN service s ON s.service_code = t.service_id
                JOIN operator_window_assignment a
                    ON a.assignment_id = t.assignment_id
                WHERE t.assignment_id = ?
                  AND t.ticket_state IN ('LLAMADO', 'EN_ATENCION')
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                ActiveTicket ticket = mapActiveTicket(result);

                if (result.next()) {
                    throw new SQLException(
                            "La asignación tiene más de un turno activo."
                    );
                }

                return Optional.of(ticket);
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo consultar el turno activo de la asignación.",
                    exception
            );
        }
    }

    private ActiveTicket mapActiveTicket(ResultSet result) throws SQLException {
        try {
            return new ActiveTicket(
                    result.getLong("ticket_id"),
                    result.getString("visual_code"),
                    result.getString("services_name"),
                    result.getLong("assignment_id"),
                    result.getInt("operator_id"),
                    result.getInt("window_id"),
                    TicketState.valueOf(result.getString("ticket_state"))
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException(
                    "El turno activo contiene datos inválidos.",
                    exception
            );
        }
    }
}
