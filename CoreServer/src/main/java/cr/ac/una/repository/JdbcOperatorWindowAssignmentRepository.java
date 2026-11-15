package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.OperatorWindowAssignment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Recupera asignaciones y sus fechas desde MySQL, incluidas las ya cerradas.
public class JdbcOperatorWindowAssignmentRepository
        implements OperatorWindowAssignmentRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcOperatorWindowAssignmentRepository(
            DatabaseConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public List<OperatorWindowAssignment> findAll() {
        String sql = """
                SELECT assignment_id, operator_id, window_id, started_at, ended_at
                FROM operator_window_assignment
                ORDER BY assignment_id
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            List<OperatorWindowAssignment> assignments = new ArrayList<>();

            while (result.next()) {
                assignments.add(mapAssignment(result));
            }

            return List.copyOf(assignments);

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudieron consultar las asignaciones de atención.",
                    exception
            );
        }
    }

    // Una sola consulta con JOIN en lugar de leer todas las asignaciones y revisar cada una por
    // separado. La hora se recibe del servidor de aplicaciones, igual que antes, para no depender del
    // reloj ni de la zona horaria de MySQL.
    @Override
    public List<OperatorWindowAssignment> findUsableByOperator(int operatorId, LocalDateTime now) {
        String sql = """
                SELECT a.assignment_id, a.operator_id, a.window_id, a.started_at, a.ended_at
                FROM operator_window_assignment a
                JOIN `operator` o
                    ON o.operator_id = a.operator_id AND o.operator_state = 'ACTIVO'
                JOIN `window` w
                    ON w.window_id = a.window_id AND w.window_state IN ('ACTIVA', 'PAUSADA')
                WHERE a.operator_id = ?
                  AND a.ended_at IS NULL
                  AND a.started_at <= ?
                ORDER BY a.assignment_id
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, operatorId);
            statement.setTimestamp(2, Timestamp.valueOf(now));

            try (ResultSet result = statement.executeQuery()) {
                List<OperatorWindowAssignment> assignments = new ArrayList<>();

                while (result.next()) {
                    assignments.add(mapAssignment(result));
                }

                return List.copyOf(assignments);
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudieron consultar las asignaciones del funcionario.",
                    exception
            );
        }
    }

    @Override
    public Optional<OperatorWindowAssignment> findById(long assignmentId) {
        String sql = """
                SELECT assignment_id, operator_id, window_id, started_at, ended_at
                FROM operator_window_assignment
                WHERE assignment_id = ?
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapAssignment(result));
                }

                return Optional.empty();
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo buscar la asignación solicitada.",
                    exception
            );
        }
    }

    private OperatorWindowAssignment mapAssignment(
            ResultSet result
    ) throws SQLException {
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
            throw new SQLException(
                    "La asignación contiene identificadores o fechas inválidos.",
                    exception
            );
        }
    }
}
