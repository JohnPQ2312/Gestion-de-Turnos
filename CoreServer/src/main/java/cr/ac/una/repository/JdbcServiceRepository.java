package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.Service;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcServiceRepository implements ServiceRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcServiceRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public List<Service> findAll() {
        String sql = """
                SELECT service_code, services_name, service_prefix
                FROM service
                WHERE service_state = 'ACTIVO'
                ORDER BY service_prefix
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            List<Service> services = new ArrayList<>();

            while (result.next()) {
                services.add(mapService(result));
            }

            return List.copyOf(services);

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo consultar el catálogo de servicios.",
                    exception
            );
        }
    }

    @Override
    public Optional<Service> findByCode(String code) {
        String sql = """
                SELECT service_code, services_name, service_prefix
                FROM service
                WHERE service_code = ? AND service_state = 'ACTIVO'
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapService(result));
                }

                return Optional.empty();
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo buscar el servicio solicitado.",
                    exception
            );
        }
    }

    private Service mapService(ResultSet result) throws SQLException {
        return new Service(
                result.getString("service_code"),
                result.getString("services_name"),
                result.getString("service_prefix")
        );
    }
}
