package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.AssignmentCallContext;
import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.ServiceWindow;
import cr.ac.una.model.WindowState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Reúne en una conexión las lecturas previas al llamado que antes abrían cinco conexiones:
// asignación, estado del funcionario, ventanilla y servicios habilitados en común.
// No bloquea filas ni decide nada: el servicio aplica las mismas reglas que antes y la transacción
// de JdbcTicketCallingRepository vuelve a comprobarlas con bloqueos antes de llamar.
public class JdbcAssignmentCallContextRepository implements AssignmentCallContextRepository {

    private static final String CONTEXT_SQL = """
            SELECT a.assignment_id, a.operator_id, a.window_id, a.started_at, a.ended_at,
                   o.operator_state, w.window_id AS found_window_id, w.window_state, w.attention_mode
            FROM operator_window_assignment a
            LEFT JOIN `operator` o ON o.operator_id = a.operator_id
            LEFT JOIN `window` w ON w.window_id = a.window_id
            WHERE a.assignment_id = ?
            """;

    // Misma intersección que antes (servicios del funcionario y de la ventanilla, sin filtrar su
    // estado): un servicio inactivo lo rechaza después la transacción con su propio mensaje.
    private static final String SHARED_SERVICES_SQL = """
            SELECT os.service_code
            FROM operator_service os
            JOIN window_service ws ON ws.service_code = os.service_code
            WHERE os.operator_id = ? AND ws.window_id = ?
            """;

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcAssignmentCallContextRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public Optional<AssignmentCallContext> findCallContext(long assignmentId) {
        try (Connection connection = connectionFactory.openConnection()) {
            OperatorWindowAssignment assignment;
            boolean operatorActive;
            ServiceWindow window;

            try (PreparedStatement statement = connection.prepareStatement(CONTEXT_SQL)) {
                statement.setLong(1, assignmentId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    assignment = mapAssignment(result);
                    operatorActive = "ACTIVO".equals(result.getString("operator_state"));
                    window = mapWindow(result);
                }
            }

            Set<String> shared = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(SHARED_SERVICES_SQL)) {
                statement.setInt(1, assignment.getOperatorId());
                statement.setInt(2, assignment.getWindowId());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        shared.add(result.getString("service_code"));
                    }
                }
            }

            return Optional.of(new AssignmentCallContext(assignment, operatorActive, window, shared));

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudieron consultar los datos de la asignación para el llamado.",
                    exception
            );
        }
    }

    private OperatorWindowAssignment mapAssignment(ResultSet result) throws SQLException {
        Timestamp startedAt = result.getTimestamp("started_at");
        Timestamp endedAt = result.getTimestamp("ended_at");
        if (startedAt == null) {
            throw new SQLException("La asignación no tiene fecha de inicio.");
        }
        try {
            return new OperatorWindowAssignment(
                    result.getLong("assignment_id"),
                    result.getInt("operator_id"),
                    result.getInt("window_id"),
                    startedAt.toLocalDateTime(),
                    endedAt == null ? null : endedAt.toLocalDateTime()
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException("La asignación contiene identificadores o fechas inválidos.", exception);
        }
    }

    // null cuando la ventanilla no existe (LEFT JOIN sin coincidencia).
    private ServiceWindow mapWindow(ResultSet result) throws SQLException {
        int windowId = result.getInt("found_window_id");
        if (result.wasNull()) {
            return null;
        }
        try {
            return new ServiceWindow(
                    windowId,
                    WindowState.valueOf(result.getString("window_state")),
                    AttentionMode.valueOf(result.getString("attention_mode"))
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SQLException(
                    "La ventanilla contiene un identificador, estado o modalidad no admitidos por la aplicación.",
                    exception
            );
        }
    }
}
