package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

public class JdbcTicketCreationRepository implements TicketCreationRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcTicketCreationRepository(
            DatabaseConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public Ticket createWithNextNumber(Service service, boolean priority) {
        Objects.requireNonNull(service, "El servicio es obligatorio.");

        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                Service activeService = findActiveService(
                        connection, service.getCode()
                );
                int nextNumber = readNextNumber(
                        connection, activeService.getCode()
                );
                Ticket ticket = new Ticket(activeService, nextNumber, priority);

                insertTicket(connection, ticket);
                updateSequence(connection, activeService.getCode(), nextNumber);

                connection.commit();
                return ticket;

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
                    "No se pudo completar el registro del turno en MySQL.",
                    exception
            );
        }
    }

    private Service findActiveService(
            Connection connection,
            String serviceCode
    ) throws SQLException {
        String sql = """
                SELECT service_code, services_name, service_prefix
                FROM service
                WHERE service_code = ? AND service_state = 'ACTIVO'
                FOR SHARE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, serviceCode);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException(
                            "El servicio solicitado no existe o está inactivo."
                    );
                }

                return new Service(
                        result.getString("service_code"),
                        result.getString("services_name"),
                        result.getString("service_prefix")
                );
            }
        }
    }

    private int readNextNumber(
            Connection connection,
            String serviceCode
    ) throws SQLException {
        String sql = """
                SELECT last_number
                FROM ticket_sequence
                WHERE service_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, serviceCode);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException(
                            "No existe un consecutivo configurado para el servicio."
                    );
                }

                long lastNumber = result.getLong("last_number");
                if (lastNumber < 0 || lastNumber >= Integer.MAX_VALUE) {
                    throw new SQLException(
                            "El consecutivo supera el rango permitido por la aplicación."
                    );
                }

                return (int) lastNumber + 1;
            }
        }
    }

    private void insertTicket(
            Connection connection,
            Ticket ticket
    ) throws SQLException {
        String sql = """
                INSERT INTO ticket (
                    service_id, sequential_number, `priority`,
                    visual_code, ticket_state, created_at
                ) VALUES (?, ?, ?, ?, 'EN_ESPERA', ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ticket.getService().getCode());
            statement.setInt(2, ticket.getSequentialNumber());
            statement.setBoolean(3, ticket.isPriority());
            statement.setString(4, ticket.getVisualCode());
            // TIMESTAMP guarda segundos: truncar evita que MySQL redondee al futuro.
            statement.setTimestamp(5, Timestamp.valueOf(ticket.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));

            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se insertó el turno esperado.");
            }
        }
    }

    private void updateSequence(
            Connection connection,
            String serviceCode,
            int nextNumber
    ) throws SQLException {
        String sql = """
                UPDATE ticket_sequence
                SET last_number = ?
                WHERE service_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, nextNumber);
            statement.setString(2, serviceCode);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se actualizó el consecutivo esperado.");
            }
        }
    }
}
