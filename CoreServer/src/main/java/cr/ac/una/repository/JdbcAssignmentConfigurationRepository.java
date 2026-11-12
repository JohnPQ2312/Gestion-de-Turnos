package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.AssignmentConfiguration;
import cr.ac.una.model.AssignmentConfiguration.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class JdbcAssignmentConfigurationRepository implements AssignmentConfigurationRepository {
    private final DatabaseConnectionFactory factory;

    public JdbcAssignmentConfigurationRepository(DatabaseConnectionFactory factory) { this.factory = Objects.requireNonNull(factory); }

    @Override
    public AssignmentConfiguration snapshot(AdministrationActor actor) {
        try (Connection c = factory.openConnection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setReadOnly(true);
            c.setAutoCommit(false);
            try {
                JdbcAdministrationAccess.require(c, actor, false);
                var operators = read(c, """
                        SELECT o.operator_id,o.operator_name,o.operator_role,o.operator_state,
                        EXISTS(SELECT 1 FROM operator_credential c WHERE c.operator_id=o.operator_id)
                        FROM `operator` o ORDER BY o.operator_id
                        """, r -> new OperatorOption(r.getInt(1), r.getString(2), r.getString(3), "ACTIVO".equals(r.getString(4)), r.getBoolean(5)));
                var windows = read(c, "SELECT window_id,window_state,attention_mode FROM `window` ORDER BY window_id",
                        r -> new WindowOption(r.getInt(1), r.getString(2), r.getString(3)));
                var services = read(c, "SELECT service_code,services_name,service_state FROM service ORDER BY service_code",
                        r -> new ServiceOption(r.getString(1), r.getString(2), "ACTIVO".equals(r.getString(3))));
                var operatorLinks = read(c, "SELECT operator_id,service_code FROM operator_service ORDER BY operator_id,service_code", r -> new ServiceLink(r.getInt(1), r.getString(2)));
                var windowLinks = read(c, "SELECT window_id,service_code FROM window_service ORDER BY window_id,service_code", r -> new ServiceLink(r.getInt(1), r.getString(2)));
                var assignments = read(c, "SELECT assignment_id,operator_id,window_id,started_at,ended_at FROM operator_window_assignment ORDER BY assignment_id DESC", r ->
                        new AssignmentEntry(r.getLong(1), r.getInt(2), r.getInt(3), r.getTimestamp(4).toLocalDateTime(),
                                r.getTimestamp(5) == null ? null : r.getTimestamp(5).toLocalDateTime()));
                c.commit();
                return new AssignmentConfiguration(operators, windows, services, operatorLinks, windowLinks, assignments);
            } catch (SQLException | RuntimeException e) { rollback(c, e); throw e; }
        } catch (SQLException e) { throw failure(e); }
    }

    @Override
    public void setOperatorService(AdministrationActor actor, int id, String code, boolean enabled) {
        setService(actor, id, code, enabled, true);
    }

    @Override
    public void setWindowService(AdministrationActor actor, int id, String code, boolean enabled) {
        setService(actor, id, code, enabled, false);
    }

    private void setService(AdministrationActor actor, int id, String code, boolean enabled, boolean operator) {
        write(c -> {
            if (operator) lockOperator(c, id, false); else lockWindow(c, id, false);
            JdbcAdministrationAccess.require(c, actor, false);
            // La emisión toma FOR SHARE sobre este servicio: no cruza una retirada a medias.
            String storedCode;
            try (PreparedStatement s = c.prepareStatement("SELECT service_code,service_state FROM service WHERE service_code=? FOR UPDATE")) {
                s.setString(1, code);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("El servicio no existe.");
                    storedCode = r.getString(1);
                    if (enabled && !"ACTIVO".equals(r.getString(2))) throw new IllegalStateException("Solo se habilitan servicios activos.");
                }
            }
            String table = operator ? "operator_service" : "window_service";
            String ownerColumn = operator ? "operator_id" : "window_id";
            if (!enabled && exists(c, "SELECT 1 FROM " + table + " WHERE " + ownerColumn + "=? AND service_code=?", id, storedCode)) {
                if (exists(c, "SELECT 1 FROM ticket WHERE service_id=? AND ticket_state='EN_ESPERA' LIMIT 1", storedCode)) {
                    throw new IllegalStateException("El servicio tiene turnos en espera; no se puede retirar esta habilitación.");
                }
                if (exists(c, """
                        SELECT 1 FROM ticket t JOIN operator_window_assignment a ON a.assignment_id=t.assignment_id
                        WHERE a.%s=? AND t.service_id=? AND t.ticket_state IN ('LLAMADO','EN_ATENCION') LIMIT 1
                        """.formatted(ownerColumn), id, storedCode)) {
                    throw new IllegalStateException("La habilitación corresponde a un turno activo; finalice la atención primero.");
                }
            }
            String sql = enabled ? "INSERT INTO " + table + "(" + ownerColumn + ",service_code) VALUES (?,?) ON DUPLICATE KEY UPDATE service_code=service_code"
                    : "DELETE FROM " + table + " WHERE " + ownerColumn + "=? AND service_code=?";
            try (PreparedStatement s = c.prepareStatement(sql)) { s.setInt(1, id); s.setString(2, storedCode); s.executeUpdate(); }
            // Comprueba la configuración después del cambio; si pierde compatibilidad, revierte.
            String assignmentSql = "SELECT operator_id,window_id FROM operator_window_assignment WHERE " + ownerColumn + "=? AND ended_at IS NULL";
            try (PreparedStatement s = c.prepareStatement(assignmentSql)) {
                s.setInt(1, id);
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) requireCompatible(c, r.getInt(1), r.getInt(2));
                }
            }
            return null;
        });
    }

    @Override
    public long openAssignment(AdministrationActor actor, int operatorId, int windowId) {
        return write(c -> {
            // Todavía no existe una fila de asignación. Las ocupaciones se serializan por padres.
            lockOperator(c, operatorId, true);
            lockWindow(c, windowId, true);
            JdbcAdministrationAccess.require(c, actor, false);
            if (exists(c, "SELECT 1 FROM operator_window_assignment WHERE ended_at IS NULL AND (operator_id=? OR window_id=?) LIMIT 1", operatorId, windowId)) {
                throw new IllegalStateException("El funcionario o la ventanilla ya tienen una asignación abierta.");
            }
            requireCompatible(c, operatorId, windowId);
            try (PreparedStatement s = c.prepareStatement("INSERT INTO operator_window_assignment(operator_id,window_id,started_at) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                s.setInt(1, operatorId); s.setInt(2, windowId);
                // Las fechas de asignación conservan TIMESTAMP sin fracciones.
                s.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
                s.executeUpdate();
                try (ResultSet r = s.getGeneratedKeys()) {
                    if (!r.next()) throw new SQLException("No se recibió la nueva asignación.");
                    return r.getLong(1);
                }
            }
        });
    }

    @Override
    public void closeAssignment(AdministrationActor actor, long id) {
        write(c -> {
            int operatorId, windowId;
            LocalDateTime start;
            try (PreparedStatement s = c.prepareStatement("SELECT operator_id,window_id,started_at,ended_at FROM operator_window_assignment WHERE assignment_id=? FOR UPDATE")) {
                s.setLong(1, id);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("La asignación no existe.");
                    if (r.getTimestamp(4) != null) throw new IllegalStateException("La asignación ya está cerrada.");
                    operatorId = r.getInt(1); windowId = r.getInt(2); start = r.getTimestamp(3).toLocalDateTime();
                }
            }
            lockOperator(c, operatorId, false);
            String windowState = lockWindow(c, windowId, false);
            if ("PAUSADA".equals(windowState)) {
                throw new IllegalStateException("Reanude la ventanilla antes de cerrar su asignación; no debe quedar pausada sin operador.");
            }
            JdbcAdministrationAccess.require(c, actor, false);
            if (exists(c, "SELECT 1 FROM ticket WHERE assignment_id=? AND ticket_state IN ('LLAMADO','EN_ATENCION') LIMIT 1", id)) {
                throw new IllegalStateException("La asignación tiene un turno activo; finalice la atención antes de cerrarla.");
            }
            LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            if (end.isBefore(start)) throw new IllegalStateException("El inicio es posterior al reloj actual; no se puede cerrar la asignación.");
            try (PreparedStatement s = c.prepareStatement("UPDATE operator_window_assignment SET ended_at=? WHERE assignment_id=? AND ended_at IS NULL")) {
                s.setTimestamp(1, Timestamp.valueOf(end)); s.setLong(2, id);
                if (s.executeUpdate() != 1) throw new SQLException("No se cerró la asignación esperada.");
            }
            return null;
        });
    }

    private static void lockOperator(Connection c, int id, boolean opening) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT operator_state,operator_role FROM `operator` WHERE operator_id=? FOR UPDATE")) {
            s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("El funcionario no existe.");
                if (opening && (!"ACTIVO".equals(r.getString(1)) || !Set.of("OPERADOR", "ADMIN").contains(r.getString(2)))) {
                    throw new IllegalStateException("El funcionario debe estar activo y tener un perfil que pueda atender.");
                }
            }
        }
        if (opening && !exists(c, "SELECT 1 FROM operator_credential WHERE operator_id=?", id)) {
            throw new IllegalStateException("Configure una credencial para el funcionario antes de asignarlo.");
        }
    }

    private static String lockWindow(Connection c, int id, boolean opening) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT window_state FROM `window` WHERE window_id=? FOR UPDATE")) {
            s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("La ventanilla no existe.");
                if (opening && !"ACTIVA".equals(r.getString(1))) throw new IllegalStateException("La ventanilla debe estar activa y sin pausa para abrir una asignación.");
                return r.getString(1);
            }
        }
    }

    private static void requireCompatible(Connection c, int operatorId, int windowId) throws SQLException {
        if (!exists(c, """
                SELECT 1 FROM operator_service os JOIN window_service ws ON ws.service_code=os.service_code
                JOIN service s ON s.service_code=os.service_code
                WHERE os.operator_id=? AND ws.window_id=? AND s.service_state='ACTIVO' LIMIT 1 FOR SHARE
                """, operatorId, windowId)) {
            throw new IllegalStateException("El funcionario y la ventanilla deben compartir al menos un servicio activo.");
        }
    }

    @FunctionalInterface private interface Row<T> { T map(ResultSet r) throws SQLException; }
    private static <T> List<T> read(Connection c, String sql, Row<T> mapper) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            List<T> rows = new ArrayList<>(); while (r.next()) rows.add(mapper.map(r)); return List.copyOf(rows);
        }
    }
    private static boolean exists(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }
    @FunctionalInterface private interface Write<T> { T run(Connection c) throws SQLException; }
    private <T> T write(Write<T> action) {
        try (Connection c = factory.openConnection(); RoutingConfigurationLock guard = new RoutingConfigurationLock(c)) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try { T result = action.run(c); c.commit(); return result; }
            catch (SQLException | RuntimeException e) { rollback(c, e); throw e; }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalStateException("El funcionario o la ventanilla ya tienen una asignación abierta.");
            throw failure(e);
        }
    }
    private static void rollback(Connection c, Throwable error) { try { c.rollback(); } catch (SQLException e) { error.addSuppressed(e); } }
    private static DataAccessException failure(SQLException e) { return new DataAccessException("No se pudo completar la configuración de atención.", e); }
}
