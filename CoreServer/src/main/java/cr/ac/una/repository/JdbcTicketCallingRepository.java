package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public class JdbcTicketCallingRepository implements TicketCallingRepository {

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcTicketCallingRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "La fábrica de conexiones es obligatoria."
        );
    }

    @Override
    public Optional<Ticket> callNext(
            OperatorWindowAssignment assignment,
            Set<String> eligibleServiceCodes,
            AttentionMode attentionMode
    ) {
        Objects.requireNonNull(assignment, "La asignación es obligatoria.");
        Objects.requireNonNull(eligibleServiceCodes, "Los servicios son obligatorios.");
        Objects.requireNonNull(attentionMode, "La modalidad es obligatoria.");
        Set<String> requestedCodes = Set.copyOf(eligibleServiceCodes);

        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                OperatorWindowAssignment currentAssignment
                        = lockAssignment(connection, assignment);
                lockActiveOperator(connection, currentAssignment.getOperatorId());
                AttentionMode currentMode = lockActiveWindow(
                        connection, currentAssignment.getWindowId()
                );

                if (currentMode != attentionMode) {
                    throw new IllegalStateException(
                            "La modalidad de la ventanilla cambió. Consulte nuevamente."
                    );
                }

                Map<String, Service> services = findEligibleServices(
                        connection, currentAssignment, requestedCodes
                );

                if (services.isEmpty()) {
                    throw new IllegalStateException(
                            "No hay servicios activos compatibles con la solicitud, "
                            + "el operador y la ventanilla."
                    );
                }

                rejectOccupiedAssignment(connection, currentAssignment);
                Optional<Ticket> next = selectAndCall(
                        connection, currentAssignment, services, currentMode
                );

                connection.commit();
                return next;

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
                    "No se pudo completar el llamado del turno en MySQL.",
                    exception
            );
        }
    }

    private OperatorWindowAssignment lockAssignment(
            Connection connection,
            OperatorWindowAssignment requested
    ) throws SQLException {
        String sql = """
                SELECT assignment_id, operator_id, window_id, started_at, ended_at
                FROM operator_window_assignment
                WHERE assignment_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requested.getAssignmentId());

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("La asignación ya no existe.");
                }

                int operatorId = result.getInt("operator_id");
                int windowId = result.getInt("window_id");
                Timestamp startedAt = result.getTimestamp("started_at");
                Timestamp endedAt = result.getTimestamp("ended_at");

                if (startedAt == null) {
                    throw new SQLException("La asignación no tiene fecha de inicio.");
                }

                if (operatorId != requested.getOperatorId()
                        || windowId != requested.getWindowId()) {
                    throw new IllegalStateException(
                            "La asignación cambió. Consulte nuevamente."
                    );
                }

                if (endedAt != null
                        || startedAt.toLocalDateTime().isAfter(LocalDateTime.now())) {
                    throw new IllegalStateException(
                            "La asignación debe estar abierta y haber iniciado."
                    );
                }

                return new OperatorWindowAssignment(
                        requested.getAssignmentId(), operatorId, windowId,
                        startedAt.toLocalDateTime(), null
                );
            }
        }
    }

    private void lockActiveOperator(
            Connection connection,
            int operatorId
    ) throws SQLException {
        String sql = """
                SELECT operator_state
                FROM `operator`
                WHERE operator_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, operatorId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !"ACTIVO".equals(result.getString("operator_state"))) {
                    throw new IllegalStateException(
                            "El operador no existe o está inactivo."
                    );
                }
            }
        }
    }

    private AttentionMode lockActiveWindow(
            Connection connection,
            int windowId
    ) throws SQLException {
        String sql = """
                SELECT window_state, attention_mode
                FROM `window`
                WHERE window_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, windowId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !"ACTIVA".equals(result.getString("window_state"))) {
                    throw new IllegalStateException(
                            "La ventanilla no existe o está inactiva."
                    );
                }

                try {
                    return AttentionMode.valueOf(result.getString("attention_mode"));
                } catch (IllegalArgumentException exception) {
                    throw new SQLException("La modalidad de atención es inválida.", exception);
                }
            }
        }
    }

    private Map<String, Service> findEligibleServices(
            Connection connection,
            OperatorWindowAssignment assignment,
            Set<String> requestedCodes
    ) throws SQLException {
        String sql = """
                SELECT s.service_code, s.services_name, s.service_prefix
                FROM service s
                JOIN operator_service os ON os.service_code = s.service_code
                JOIN window_service ws ON ws.service_code = s.service_code
                WHERE os.operator_id = ? AND ws.window_id = ?
                  AND s.service_state = 'ACTIVO'
                ORDER BY s.service_code
                FOR SHARE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assignment.getOperatorId());
            statement.setInt(2, assignment.getWindowId());

            try (ResultSet result = statement.executeQuery()) {
                Map<String, Service> services = new TreeMap<>();

                while (result.next()) {
                    String code = result.getString("service_code");

                    if (requestedCodes.contains(code)) {
                        try {
                            services.put(code, new Service(
                                    code,
                                    result.getString("services_name"),
                                    result.getString("service_prefix")
                            ));
                        } catch (IllegalArgumentException exception) {
                            throw new SQLException("El servicio contiene datos inválidos.", exception);
                        }
                    }
                }

                return services;
            }
        }
    }

    private void rejectOccupiedAssignment(
            Connection connection,
            OperatorWindowAssignment assignment
    ) throws SQLException {
        String sql = """
                SELECT t.ticket_id
                FROM ticket t
                JOIN operator_window_assignment a ON a.assignment_id = t.assignment_id
                WHERE t.ticket_state IN ('LLAMADO', 'EN_ATENCION')
                  AND (a.assignment_id = ? OR a.operator_id = ? OR a.window_id = ?)
                LIMIT 1
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignment.getAssignmentId());
            statement.setInt(2, assignment.getOperatorId());
            statement.setInt(3, assignment.getWindowId());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalStateException(
                            "La asignación, el operador o la ventanilla "
                            + "ya tienen un turno activo."
                    );
                }
            }
        }
    }

    private Optional<Ticket> selectAndCall(
            Connection connection,
            OperatorWindowAssignment assignment,
            Map<String, Service> services,
            AttentionMode attentionMode
    ) throws SQLException {
        String placeholders = String.join(", ", Collections.nCopies(services.size(), "?"));
        String sql = """
                SELECT ticket_id, service_id, sequential_number, `priority`,
                       visual_code, created_at
                FROM ticket
                WHERE ticket_state = 'EN_ESPERA'
                  AND service_id IN (%s)
                  AND (? = FALSE OR `priority` = TRUE)
                ORDER BY `priority` DESC, created_at, ticket_id
                LIMIT 1
                FOR UPDATE
                """.formatted(placeholders);

        long ticketId;
        Ticket ticket;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (String code : services.keySet()) {
                statement.setString(parameter++, code);
            }
            statement.setBoolean(parameter, attentionMode == AttentionMode.PRIORITY_ONLY);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                ticketId = result.getLong("ticket_id");
                ticket = mapWaitingTicket(result, services);
            }
        }

        ticket.markCalled(assignment);
        updateCalledTicket(connection, ticketId, ticket);
        return Optional.of(ticket);
    }

    private Ticket mapWaitingTicket(
            ResultSet result,
            Map<String, Service> services
    ) throws SQLException {
        Timestamp createdAt = result.getTimestamp("created_at");
        Service service = services.get(result.getString("service_id"));

        if (createdAt == null || service == null) {
            throw new SQLException("El turno no tiene un servicio o fecha válidos.");
        }

        Ticket ticket;
        try {
            ticket = new Ticket(
                    service,
                    result.getInt("sequential_number"),
                    result.getBoolean("priority"),
                    createdAt.toLocalDateTime()
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException("El turno contiene datos inválidos.", exception);
        }

        if (!ticket.getVisualCode().equals(result.getString("visual_code"))) {
            throw new SQLException(
                    "El código guardado del turno no coincide con el prefijo del servicio."
            );
        }

        return ticket;
    }

    private void updateCalledTicket(
            Connection connection,
            long ticketId,
            Ticket ticket
    ) throws SQLException {
        String sql = """
                UPDATE ticket
                SET assignment_id = ?, ticket_state = 'LLAMADO', called_at = ?
                WHERE ticket_id = ? AND ticket_state = 'EN_ESPERA'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticket.getAssignmentId());
            statement.setTimestamp(2, Timestamp.valueOf(ticket.getCalledAt()));
            statement.setLong(3, ticketId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se actualizó el turno seleccionado para el llamado.");
            }
        }
    }
}
