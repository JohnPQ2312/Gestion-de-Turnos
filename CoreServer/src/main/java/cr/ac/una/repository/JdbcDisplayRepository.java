package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.ActiveDisplaySnapshot;
import cr.ac.una.model.DisplaySnapshot;
import cr.ac.una.model.DisplayTicket;
import cr.ac.una.model.TicketState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Lee las capturas públicas desde una misma lectura consistente de MySQL.
// Las listas y conteos de cada captura comparten la transacción para presentar un estado coherente.
public class JdbcDisplayRepository implements DisplayRepository {

    private static final String SELECT_TICKETS = """
            SELECT t.ticket_id, t.visual_code, s.services_name,
                   t.`priority`, t.ticket_state, a.window_id, t.called_at, t.last_called_at, t.call_count
            FROM ticket t
            JOIN service s ON s.service_code = t.service_id
            LEFT JOIN operator_window_assignment a
                ON a.assignment_id = t.assignment_id
            """;

    // Cuenta con el mismo JOIN de la lista de espera para que el total sea comparable.
    private static final String COUNT_WAITING = """
            SELECT COUNT(*)
            FROM ticket t
            JOIN service s ON s.service_code = t.service_id
            WHERE t.ticket_state = 'EN_ESPERA'
            """;

    private final DatabaseConnectionFactory connectionFactory;

    public JdbcDisplayRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "La fábrica de conexiones es obligatoria."
        );
    }

    // Lee ambas listas con REPEATABLE_READ para que compartan la misma captura de datos.
    @Override
    public DisplaySnapshot findSnapshot(int waitingLimit, int recentCallsLimit) {
        validateLimit(waitingLimit);
        validateLimit(recentCallsLimit);

        return inSnapshot(connection -> new DisplaySnapshot(
                findWaitingTickets(connection, waitingLimit),
                findRecentCalls(connection, recentCallsLimit)
        ));
    }

    // Consulta llamados/en atención y espera por separado. Los llamados se leen completos, sin
    // límite: así ningún activo queda oculto y el anuncio siempre ve el LLAMADO más reciente.
    // La espera filtra su estado en SQL antes del LIMIT, de modo que los finalizados nunca ocupan
    // lugares. Listas y conteo se leen en la misma transacción.
    @Override
    public ActiveDisplaySnapshot findActiveSnapshot(int waitingLimit) {
        validateLimit(waitingLimit);

        return inSnapshot(connection -> new ActiveDisplaySnapshot(
                findActiveCalls(connection),
                findWaitingTickets(connection, waitingLimit),
                countWaiting(connection)
        ));
    }

    @FunctionalInterface
    private interface SnapshotReader<T> {

        T read(Connection connection) throws SQLException;
    }

    private <T> T inSnapshot(SnapshotReader<T> reader) {
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            try {
                T snapshot = reader.read(connection);
                connection.commit();
                return snapshot;

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
                    "No se pudo consultar la información de la pantalla pública.",
                    exception
            );
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "El límite de resultados debe estar entre 1 y 100."
            );
        }
    }

    private List<DisplayTicket> findWaitingTickets(
            Connection connection, int limit
    ) throws SQLException {
        String sql = SELECT_TICKETS + """
                WHERE t.ticket_state = 'EN_ESPERA'
                ORDER BY t.`priority` DESC, t.created_at ASC, t.ticket_id ASC
                LIMIT ?
                """;

        return queryTickets(connection, sql, limit);
    }

    // Ordena por el último llamado, de modo que un rellamado vuelva al inicio del historial
    // público.
    private List<DisplayTicket> findRecentCalls(
            Connection connection, int limit
    ) throws SQLException {
        String sql = SELECT_TICKETS + """
                WHERE t.ticket_state IN ('LLAMADO', 'EN_ATENCION', 'FINALIZADO')
                ORDER BY t.last_called_at DESC, t.ticket_id DESC
                LIMIT ?
                """;

        return queryTickets(connection, sql, limit);
    }

    // Orden por último llamado: un rellamado vuelve al inicio y el primer LLAMADO de la lista
    // es el anuncio vigente. Sin LIMIT: el volumen está acotado por las asignaciones abiertas.
    private List<DisplayTicket> findActiveCalls(Connection connection) throws SQLException {
        String sql = SELECT_TICKETS + """
                WHERE t.ticket_state IN ('LLAMADO', 'EN_ATENCION')
                ORDER BY t.last_called_at DESC, t.ticket_id DESC
                """;

        List<DisplayTicket> tickets = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                tickets.add(mapTicket(result));
            }
        }
        return tickets;
    }

    private int countWaiting(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_WAITING);
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("No se pudo contar los turnos de la pantalla pública.");
            }
            long count = result.getLong(1);
            if (count < 0 || count > Integer.MAX_VALUE) {
                throw new SQLException("La cantidad de turnos no es válida.");
            }
            return (int) count;
        }
    }

    private List<DisplayTicket> queryTickets(
            Connection connection, String sql, int limit
    ) throws SQLException {
        List<DisplayTicket> tickets = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tickets.add(mapTicket(result));
                }
            }
        }

        return tickets;
    }

    private DisplayTicket mapTicket(ResultSet result) throws SQLException {
        int windowValue = result.getInt("window_id");
        Integer windowId = result.wasNull() ? null : windowValue;
        Timestamp calledAt = result.getTimestamp("called_at");
        Timestamp lastCalledAt = result.getTimestamp("last_called_at");

        try {
            return new DisplayTicket(
                    result.getLong("ticket_id"),
                    result.getString("visual_code"),
                    result.getString("services_name"),
                    result.getBoolean("priority"),
                    TicketState.valueOf(result.getString("ticket_state")),
                    windowId,
                    calledAt == null ? null : calledAt.toLocalDateTime(),
                    lastCalledAt == null ? null : lastCalledAt.toLocalDateTime(),
                    result.getInt("call_count")
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SQLException(
                    "Un turno contiene datos inválidos para la pantalla pública.",
                    exception
            );
        }
    }
}
