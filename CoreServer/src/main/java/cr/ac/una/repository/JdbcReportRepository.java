package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.AttendedTicket;
import cr.ac.una.model.OperatorAttentionCount;
import cr.ac.una.model.ReportPeriod;
import cr.ac.una.model.ServiceTicketCount;
import cr.ac.una.model.TicketDuration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Obtiene los reportes de MySQL usando intervalos de inicio incluido y fin excluido.
// Las duraciones se calculan con TIMESTAMPDIFF sobre las fechas guardadas.
// El responsable se obtiene de la asignación histórica del ticket.
public class JdbcReportRepository implements ReportRepository {

    private static final String ATTENDED = """
            SELECT t.visual_code, s.services_name, t.`priority`,
                   a.window_id, a.operator_id, o.operator_name,
                   t.created_at, t.started_at, t.ended_at
            FROM ticket t
            JOIN service s ON s.service_code = t.service_id
            JOIN operator_window_assignment a ON a.assignment_id = t.assignment_id
            JOIN `operator` o ON o.operator_id = a.operator_id
            WHERE t.ticket_state = 'FINALIZADO'
              AND t.ended_at >= ? AND t.ended_at < ?
            ORDER BY t.ended_at, t.ticket_id
            """;

    private static final String BY_OPERATOR = """
            SELECT o.operator_id, o.operator_name, COUNT(t.ticket_id) AS attended
            FROM `operator` o
            LEFT JOIN operator_window_assignment a ON a.operator_id = o.operator_id
            LEFT JOIN ticket t ON t.assignment_id = a.assignment_id
                 AND t.ticket_state = 'FINALIZADO'
                 AND t.ended_at >= ? AND t.ended_at < ?
            GROUP BY o.operator_id, o.operator_name
            ORDER BY attended DESC, o.operator_id
            """;

    private static final String BY_SERVICE = """
            SELECT s.service_code, s.services_name,
                   COUNT(t.ticket_id) AS issued
            FROM service s
            LEFT JOIN ticket t ON t.service_id = s.service_code
                 AND t.created_at >= ? AND t.created_at < ?
            GROUP BY s.service_code, s.services_name
            ORDER BY s.service_code
            """;

    private static final String WAIT_TIMES = """
            SELECT t.visual_code, s.services_name, t.created_at AS from_time,
                   t.started_at AS to_time,
                   TIMESTAMPDIFF(SECOND, t.created_at, t.started_at) AS seconds
            FROM ticket t
            JOIN service s ON s.service_code = t.service_id
            WHERE t.started_at IS NOT NULL
              AND t.started_at >= ? AND t.started_at < ?
            ORDER BY t.started_at, t.ticket_id
            """;

    private static final String ATTENTION_TIMES = """
            SELECT t.visual_code, s.services_name, t.started_at AS from_time,
                   t.ended_at AS to_time,
                   TIMESTAMPDIFF(SECOND, t.started_at, t.ended_at) AS seconds
            FROM ticket t
            JOIN service s ON s.service_code = t.service_id
            WHERE t.ticket_state = 'FINALIZADO'
              AND t.ended_at >= ? AND t.ended_at < ?
            ORDER BY t.ended_at, t.ticket_id
            """;

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcReportRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public List<AttendedTicket> findAttended(ReportPeriod period) {
        return query(ATTENDED, period, "los turnos atendidos", result -> new AttendedTicket(
                result.getString("visual_code"),
                result.getString("services_name"),
                result.getBoolean("priority"),
                result.getInt("window_id"),
                result.getInt("operator_id"),
                result.getString("operator_name"),
                result.getTimestamp("created_at").toLocalDateTime(),
                result.getTimestamp("started_at").toLocalDateTime(),
                result.getTimestamp("ended_at").toLocalDateTime()
        ));
    }

    @Override
    public List<OperatorAttentionCount> countAttendedByOperator(ReportPeriod period) {
        return query(BY_OPERATOR, period, "la atención por funcionario",
                result -> new OperatorAttentionCount(
                        result.getInt("operator_id"),
                        result.getString("operator_name"),
                        result.getLong("attended")
                ));
    }

    @Override
    public List<ServiceTicketCount> countByService(ReportPeriod period) {
        return query(BY_SERVICE, period, "los turnos por servicio",
                result -> new ServiceTicketCount(
                        result.getString("service_code"),
                        result.getString("services_name"),
                        result.getLong("issued")
                ));
    }

    @Override
    public List<TicketDuration> findWaitTimes(ReportPeriod period) {
        return query(WAIT_TIMES, period, "los tiempos de espera", this::mapDuration);
    }

    @Override
    public List<TicketDuration> findAttentionTimes(ReportPeriod period) {
        return query(ATTENTION_TIMES, period, "los tiempos de atención", this::mapDuration);
    }

    private TicketDuration mapDuration(ResultSet result) throws SQLException {
        return new TicketDuration(
                result.getString("visual_code"),
                result.getString("services_name"),
                result.getTimestamp("from_time").toLocalDateTime(),
                result.getTimestamp("to_time").toLocalDateTime(),
                result.getLong("seconds")
        );
    }

    @FunctionalInterface
    private interface RowMapper<T> {

        T map(ResultSet result) throws SQLException;
    }

    private <T> List<T> query(
            String sql, ReportPeriod period, String description, RowMapper<T> mapper
    ) {
        Objects.requireNonNull(period, "El período es obligatorio.");

        try (Connection connection = connectionFactory.openConnection()) {
            connection.setReadOnly(true);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.valueOf(period.startInclusive()));
                statement.setTimestamp(2, Timestamp.valueOf(period.endExclusive()));

                try (ResultSet result = statement.executeQuery()) {
                    List<T> rows = new ArrayList<>();
                    while (result.next()) {
                        rows.add(mapper.map(result));
                    }
                    return List.copyOf(rows);
                }
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo consultar " + description + ".", exception
            );
        }
    }
}
