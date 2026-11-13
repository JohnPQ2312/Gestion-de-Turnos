package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// Consulta el estado del funcionario y sus servicios habilitados en MySQL.
public class JdbcOperatorRepository implements OperatorRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcOperatorRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public boolean isActive(int operatorId) {
        String sql = """
                SELECT 1
                FROM `operator`
                WHERE operator_id = ? AND operator_state = 'ACTIVO'
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, operatorId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo consultar el estado del operador.",
                    exception
            );
        }
    }

    @Override
    public Set<String> findServiceCodes(int operatorId) {
        String sql = """
                SELECT service_code
                FROM operator_service
                WHERE operator_id = ?
                """;

        try (
                Connection connection = connectionFactory.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, operatorId);

            try (ResultSet result = statement.executeQuery()) {
                Set<String> serviceCodes = new HashSet<>();

                while (result.next()) {
                    serviceCodes.add(result.getString("service_code"));
                }

                return Set.copyOf(serviceCodes);
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudieron consultar los servicios del operador.",
                    exception
            );
        }
    }
}
