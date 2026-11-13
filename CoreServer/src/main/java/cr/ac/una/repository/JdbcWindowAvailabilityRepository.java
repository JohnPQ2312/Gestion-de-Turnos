package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.ServiceWindow;
import cr.ac.una.model.WindowState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

// Pausa o reanuda la ventanilla sin modificar el turno en curso.
// Bloquea la asignación antes de la ventanilla, como el llamado, para coordinar ambas
// operaciones.
public class JdbcWindowAvailabilityRepository implements WindowAvailabilityRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcWindowAvailabilityRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public Optional<ServiceWindow> findByAssignmentId(long assignmentId) {
        String sql = """
                SELECT w.window_id, w.window_state, w.attention_mode
                FROM operator_window_assignment a
                JOIN `window` w ON w.window_id = a.window_id
                WHERE a.assignment_id = ?
                """;

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapWindow(result)) : Optional.empty();
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo consultar el estado de la ventanilla.", exception
            );
        }
    }

    @Override
    public ServiceWindow pause(long assignmentId) {
        return changeState(assignmentId, WindowState.ACTIVA, WindowState.PAUSADA);
    }

    @Override
    public ServiceWindow resume(long assignmentId) {
        return changeState(assignmentId, WindowState.PAUSADA, WindowState.ACTIVA);
    }

    private ServiceWindow changeState(long assignmentId, WindowState from, WindowState to) {
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                int windowId = lockOpenAssignment(connection, assignmentId);
                ServiceWindow window = lockWindow(connection, windowId);
                requireState(window, from, to);
                updateState(connection, windowId, from, to);

                connection.commit();
                return new ServiceWindow(windowId, to, window.getAttentionMode());

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
                    "No se pudo cambiar el estado de la ventanilla.", exception
            );
        }
    }

    private int lockOpenAssignment(Connection connection, long assignmentId) throws SQLException {
        String sql = """
                SELECT window_id, started_at, ended_at
                FROM operator_window_assignment
                WHERE assignment_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("La asignación ya no existe.");
                }

                Timestamp startedAt = result.getTimestamp("started_at");
                if (result.getTimestamp("ended_at") != null || startedAt == null
                        || startedAt.toLocalDateTime().isAfter(LocalDateTime.now())) {
                    throw new IllegalStateException(
                            "La asignación debe estar abierta y haber iniciado."
                    );
                }

                return result.getInt("window_id");
            }
        }
    }

    private ServiceWindow lockWindow(Connection connection, int windowId) throws SQLException {
        String sql = """
                SELECT window_id, window_state, attention_mode
                FROM `window`
                WHERE window_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, windowId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("La ventanilla ya no existe.");
                }
                return mapWindow(result);
            }
        }
    }

    private static void requireState(ServiceWindow window, WindowState from, WindowState to) {
        if (window.getState() == from) {
            return;
        }
        if (window.getState() == WindowState.INACTIVA) {
            throw new IllegalStateException(
                    "La ventanilla está dada de baja; su estado lo cambia la administración."
            );
        }
        throw new IllegalStateException(to == WindowState.PAUSADA
                ? "La ventanilla ya está en pausa."
                : "La ventanilla no está en pausa.");
    }

    private void updateState(Connection connection, int windowId, WindowState from, WindowState to)
            throws SQLException {
        String sql = """
                UPDATE `window`
                SET window_state = ?
                WHERE window_id = ? AND window_state = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, to.name());
            statement.setInt(2, windowId);
            statement.setString(3, from.name());

            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se actualizó el estado de la ventanilla.");
            }
        }
    }

    private static ServiceWindow mapWindow(ResultSet result) throws SQLException {
        try {
            return new ServiceWindow(
                    result.getInt("window_id"),
                    WindowState.valueOf(result.getString("window_state")),
                    AttentionMode.valueOf(result.getString("attention_mode"))
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException("La ventanilla contiene un estado o modalidad inválidos.", exception);
        }
    }
}
