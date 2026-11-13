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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Consulta ventanillas y sus habilitaciones de servicio mediante JDBC.
public class JdbcServiceWindowRepository implements ServiceWindowRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcServiceWindowRepository(
            DatabaseConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public List<ServiceWindow> findAll() {
        String sql = """
                SELECT window_id, window_state, attention_mode
                FROM `window`
                ORDER BY window_id
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            List<ServiceWindow> windows = new ArrayList<>();

            while (result.next()) {
                windows.add(mapWindow(result));
            }

            return List.copyOf(windows);

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudieron consultar las ventanillas.",
                    exception
            );
        }
    }

    @Override
    public Optional<ServiceWindow> findById(int windowId) {
        String sql = """
                SELECT window_id, window_state, attention_mode
                FROM `window`
                WHERE window_id = ?
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, windowId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapWindow(result));
                }

                return Optional.empty();
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo buscar la ventanilla solicitada.",
                    exception
            );
        }
    }

    @Override
    public Set<String> findServiceCodes(int windowId) {
        String sql = """
                SELECT service_code
                FROM window_service
                WHERE window_id = ?
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, windowId);

            try (ResultSet result = statement.executeQuery()) {
                Set<String> serviceCodes = new HashSet<>();

                while (result.next()) {
                    serviceCodes.add(result.getString("service_code"));
                }

                return Set.copyOf(serviceCodes);
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudieron consultar los servicios de la ventanilla.",
                    exception
            );
        }
    }

    private ServiceWindow mapWindow(ResultSet result) throws SQLException {
        try {
            return new ServiceWindow(
                    result.getInt("window_id"),
                    WindowState.valueOf(result.getString("window_state")),
                    AttentionMode.valueOf(result.getString("attention_mode"))
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException(
                    "La ventanilla contiene un identificador, estado "
                    + "o modalidad no admitidos por la aplicación.",
                    exception
            );
        }
    }
}
