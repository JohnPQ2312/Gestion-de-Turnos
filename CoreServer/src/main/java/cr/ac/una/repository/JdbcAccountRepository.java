package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.OperatorAccount;
import cr.ac.una.model.UserRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

// Lee la identidad del funcionario junto con su credencial y guarda hashes mediante JDBC.
public class JdbcAccountRepository implements AccountRepository {

    private static final String SELECT_ACCOUNT = """
            SELECT o.operator_id, o.operator_username, o.operator_name,
                   o.operator_role, o.operator_state, c.password_hash
            FROM `operator` o
            LEFT JOIN operator_credential c ON c.operator_id = o.operator_id
            """;

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcAccountRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public Optional<OperatorAccount> findByUsername(String username) {
        Objects.requireNonNull(username, "El nombre de usuario es obligatorio.");
        return findOne(SELECT_ACCOUNT + "WHERE o.operator_username = ?", statement
                -> statement.setString(1, username));
    }

    @Override
    public Optional<OperatorAccount> findById(int operatorId) {
        return findOne(SELECT_ACCOUNT + "WHERE o.operator_id = ?", statement
                -> statement.setInt(1, operatorId));
    }

    @Override
    public void saveCredential(int operatorId, String passwordHash) {
        Objects.requireNonNull(passwordHash, "El hash de la contraseña es obligatorio.");

        String sql = """
                INSERT INTO operator_credential (operator_id, password_hash)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE password_hash = ?
                """;

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, operatorId);
            statement.setString(2, passwordHash);
            statement.setString(3, passwordHash);
            statement.executeUpdate();

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo guardar la credencial del funcionario.", exception
            );
        }
    }

    @FunctionalInterface
    private interface Binder {

        void bind(PreparedStatement statement) throws SQLException;
    }

    private Optional<OperatorAccount> findOne(String sql, Binder binder) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OperatorAccount(
                        result.getInt("operator_id"),
                        result.getString("operator_username"),
                        result.getString("operator_name"),
                        UserRole.fromDatabase(result.getString("operator_role")),
                        "ACTIVO".equals(result.getString("operator_state")),
                        result.getString("password_hash")
                ));
            }

        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No se pudo consultar la cuenta del funcionario.", exception
            );
        }
    }
}
