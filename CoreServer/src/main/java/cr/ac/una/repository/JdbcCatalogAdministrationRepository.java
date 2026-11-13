package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.ManagedService;
import java.sql.*;
import java.util.*;

// Administración de servicios.
// Orden de bloqueos de las escrituras: bloqueo nombrado de configuración → fila del catálogo → lecturas de control.
// Emisión (servicio → consecutivo) y llamado (asignación → funcionario → ventanilla → servicio → ticket)
// esperan la fila bloqueada aquí; este recorrido no bloquea después ninguna fila que ellos tengan, así no se cruzan.
public class JdbcCatalogAdministrationRepository implements CatalogAdministrationRepository {

    private static final String PENDING_STATES = "('EN_ESPERA','LLAMADO','EN_ATENCION')";
    private final DatabaseConnectionFactory factory;

    public JdbcCatalogAdministrationRepository(DatabaseConnectionFactory factory) {
        this.factory = Objects.requireNonNull(factory, "La fábrica de conexiones es obligatoria.");
    }

    // ---------------------------------------------------------------- servicios

    @Override
    public List<ManagedService> listServices(AdministrationActor actor) {
        return readOnly(actor, c -> read(c, """
                SELECT s.service_code, s.services_name, s.service_prefix, s.service_state,
                       COALESCE(q.last_number, 0),
                       (SELECT COUNT(*) FROM ticket t
                         WHERE t.service_id = s.service_code AND t.ticket_state IN %s),
                       (SELECT COUNT(DISTINCT a.assignment_id) FROM operator_window_assignment a
                         JOIN operator_service os ON os.operator_id = a.operator_id
                         JOIN window_service ws ON ws.window_id = a.window_id AND ws.service_code = os.service_code
                         WHERE a.ended_at IS NULL AND os.service_code = s.service_code),
                       COALESCE(q.last_number, 0) > 0
                         OR EXISTS(SELECT 1 FROM ticket t2 WHERE t2.service_id = s.service_code)
                FROM service s
                LEFT JOIN ticket_sequence q ON q.service_id = s.service_code
                ORDER BY s.service_code
                """.formatted(PENDING_STATES), r -> new ManagedService(r.getString(1), r.getString(2), r.getString(3),
                "ACTIVO".equals(r.getString(4)), r.getLong(5), r.getLong(6), r.getLong(7), r.getBoolean(8))));
    }

    @Override
    public void createService(AdministrationActor actor, String code, String name, String prefix) {
        write(actor, "Ya existe un servicio con ese código o prefijo.", c -> {
            if (exists(c, "SELECT 1 FROM service WHERE service_code = ?", code)) {
                throw new IllegalStateException("Ya existe un servicio con el código " + code + ".");
            }
            requireUniqueName(c, name, null);
            requireFreePrefix(c, prefix, null);
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO service (service_code, services_name, service_prefix, service_state) VALUES (?, ?, ?, 'ACTIVO')")) {
                s.setString(1, code);
                s.setString(2, name);
                s.setString(3, prefix);
                s.executeUpdate();
            }
            // El servicio y su consecutivo se crean juntos: sin la fila no se podría emitir.
            try (PreparedStatement s = c.prepareStatement("INSERT INTO ticket_sequence (service_id, last_number) VALUES (?, 0)")) {
                s.setString(1, code);
                s.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void updateService(AdministrationActor actor, String code, String name, String prefix) {
        write(actor, "Ya existe un servicio con ese prefijo.", c -> {
            StoredService current = lockService(c, code);
            requireUniqueName(c, name, current.code());
            boolean prefixChanged = !prefix.equals(current.prefix());
            if (prefixChanged) {
                if (!ManagedService.isValidNewPrefix(prefix)) {
                    throw new IllegalArgumentException("El prefijo debe tener de 1 a 5 letras mayúsculas sin tildes, sin dígitos ni espacios.");
                }
                // La emisión bloquea servicio y luego consecutivo; aquí se respeta el mismo orden.
                if (lastNumber(c, current.code(), true) > 0
                        || exists(c, "SELECT 1 FROM ticket WHERE service_id = ? LIMIT 1", current.code())) {
                    throw new IllegalStateException("El servicio ya emitió turnos: su prefijo no se puede cambiar "
                            + "para conservar los códigos históricos.");
                }
                requireFreePrefix(c, prefix, current.code());
            }
            if (!prefixChanged && name.equals(current.name())) return null;
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE service SET services_name = ?, service_prefix = ? WHERE service_code = ?")) {
                s.setString(1, name);
                s.setString(2, prefix);
                s.setString(3, current.code());
                if (s.executeUpdate() != 1) throw new SQLException("No se actualizó el servicio esperado.");
            }
            return null;
        });
    }

    @Override
    public void setServiceActive(AdministrationActor actor, String code, boolean active) {
        write(actor, null, c -> {
            StoredService current = lockService(c, code);
            if (active) {
                if (current.active()) throw new IllegalStateException("El servicio ya está activo.");
                ensureSequence(c, current.code());
            } else {
                if (!current.active()) throw new IllegalStateException("El servicio ya está inactivo.");
                // Con el servicio bloqueado no se emiten ni llaman turnos nuevos suyos: el conteo es estable.
                rejectPendingTickets(c, current.code());
                rejectOrphanedAssignments(c, current.code());
            }
            try (PreparedStatement s = c.prepareStatement("UPDATE service SET service_state = ? WHERE service_code = ?")) {
                s.setString(1, active ? "ACTIVO" : "INACTIVO");
                s.setString(2, current.code());
                if (s.executeUpdate() != 1) throw new SQLException("No se actualizó el estado del servicio.");
            }
            return null;
        });
    }

    private record StoredService(String code, String name, String prefix, boolean active) { }

    private static StoredService lockService(Connection c, String code) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT service_code, services_name, service_prefix, service_state FROM service WHERE service_code = ? FOR UPDATE")) {
            s.setString(1, code);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("El servicio no existe. Actualice el listado.");
                return new StoredService(r.getString(1), r.getString(2), r.getString(3), "ACTIVO".equals(r.getString(4)));
            }
        }
    }

    private static void requireUniqueName(Connection c, String name, String exceptCode) throws SQLException {
        // La comparación sigue la intercalación de MySQL: no distingue mayúsculas ni tildes.
        boolean duplicate = exceptCode == null
                ? exists(c, "SELECT 1 FROM service WHERE services_name = ? LIMIT 1", name)
                : exists(c, "SELECT 1 FROM service WHERE services_name = ? AND service_code <> ? LIMIT 1", name, exceptCode);
        if (duplicate) throw new IllegalStateException("Ya existe otro servicio con el nombre " + name + ".");
    }

    private static void requireFreePrefix(Connection c, String prefix, String exceptCode) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT service_code FROM service WHERE service_prefix = ?")) {
            s.setString(1, prefix);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    if (!r.getString(1).equals(exceptCode)) {
                        throw new IllegalStateException("El prefijo " + prefix + " ya pertenece al servicio " + r.getString(1) + ".");
                    }
                }
            }
        }
        // Defensa para datos anteriores: ningún código histórico de otro servicio debe empezar con el prefijo + dígitos.
        // El prefijo ya se validó como letras, por eso puede formar parte de la expresión.
        if (exists(c, "SELECT 1 FROM ticket WHERE visual_code REGEXP ? AND service_id <> ? LIMIT 1",
                "^" + prefix + "[0-9]+$", exceptCode == null ? "" : exceptCode)) {
            throw new IllegalStateException("Hay turnos históricos con códigos " + prefix + "###; elija otro prefijo.");
        }
    }

    private static long lastNumber(Connection c, String code, boolean lock) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT last_number FROM ticket_sequence WHERE service_id = ?" + (lock ? " FOR UPDATE" : ""))) {
            s.setString(1, code);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        }
    }

    // Reactivar nunca reinicia la numeración. Si faltara la fila (datos anteriores), parte del mayor consecutivo usado.
    private static void ensureSequence(Connection c, String code) throws SQLException {
        if (exists(c, "SELECT 1 FROM ticket_sequence WHERE service_id = ? FOR UPDATE", code)) return;
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO ticket_sequence (service_id, last_number)
                SELECT ?, COALESCE(MAX(sequential_number), 0) FROM ticket WHERE service_id = ?
                """)) {
            s.setString(1, code);
            s.setString(2, code);
            s.executeUpdate();
        }
    }

    private static void rejectPendingTickets(Connection c, String code) throws SQLException {
        Map<String, Long> counts = new HashMap<>();
        try (PreparedStatement s = c.prepareStatement(
                "SELECT ticket_state, COUNT(*) FROM ticket WHERE service_id = ? AND ticket_state IN " + PENDING_STATES + " GROUP BY ticket_state")) {
            s.setString(1, code);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) counts.put(r.getString(1), r.getLong(2));
            }
        }
        if (!counts.isEmpty()) {
            throw new IllegalStateException("No se puede desactivar: el servicio tiene "
                    + counts.getOrDefault("EN_ESPERA", 0L) + " en espera, "
                    + counts.getOrDefault("LLAMADO", 0L) + " llamados y "
                    + counts.getOrDefault("EN_ATENCION", 0L) + " en atención. "
                    + "Deben atenderse y finalizarse primero; no se transfieren ni cancelan turnos.");
        }
    }

    // Misma política que Asignaciones: una asignación abierta no debe quedar sin servicios activos compartidos.
    private static void rejectOrphanedAssignments(Connection c, String code) throws SQLException {
        List<Long> affected = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("""
                SELECT a.assignment_id FROM operator_window_assignment a
                WHERE a.ended_at IS NULL
                  AND EXISTS (SELECT 1 FROM operator_service os
                              JOIN window_service ws ON ws.service_code = os.service_code
                              WHERE os.operator_id = a.operator_id AND ws.window_id = a.window_id
                                AND os.service_code = ?)
                  AND NOT EXISTS (SELECT 1 FROM operator_service os
                              JOIN window_service ws ON ws.service_code = os.service_code
                              JOIN service s ON s.service_code = os.service_code
                              WHERE os.operator_id = a.operator_id AND ws.window_id = a.window_id
                                AND s.service_state = 'ACTIVO' AND s.service_code <> ?)
                ORDER BY a.assignment_id
                """)) {
            s.setString(1, code);
            s.setString(2, code);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) affected.add(r.getLong(1));
            }
        }
        if (!affected.isEmpty()) {
            throw new IllegalStateException("No se puede desactivar: las asignaciones abiertas " + affected
                    + " quedarían sin servicios activos en común. Ciérrelas o habilite otro servicio común desde Asignaciones.");
        }
    }

    // ---------------------------------------------------------------- soporte JDBC

    @FunctionalInterface private interface Row<T> { T map(ResultSet r) throws SQLException; }
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws SQLException; }

    private <T> T readOnly(AdministrationActor actor, Work<T> action) {
        try (Connection c = factory.openConnection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setReadOnly(true);
            c.setAutoCommit(false);
            try {
                JdbcAdministrationAccess.require(c, actor, false);
                T result = action.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) { rollback(c, e); throw e; }
        } catch (SQLException e) { throw failure(e); }
    }

    // Las escrituras de servicios se serializan con los cambios de habilitaciones y asignaciones.
    private <T> T write(AdministrationActor actor, String duplicateMessage, Work<T> action) {
        try (Connection c = factory.openConnection(); RoutingConfigurationLock guard = new RoutingConfigurationLock(c)) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                JdbcAdministrationAccess.require(c, actor, false);
                T result = action.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) { rollback(c, e); throw e; }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 && duplicateMessage != null) throw new IllegalStateException(duplicateMessage);
            if (e.getErrorCode() == 3819) throw new IllegalArgumentException("Los datos no cumplen las restricciones de la base de datos.");
            throw failure(e);
        }
    }

    private static <T> List<T> read(Connection c, String sql, Row<T> mapper) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (r.next()) rows.add(mapper.map(r));
            return List.copyOf(rows);
        }
    }

    private static boolean exists(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    private static void rollback(Connection c, Throwable error) {
        try { c.rollback(); } catch (SQLException e) { error.addSuppressed(e); }
    }

    private static DataAccessException failure(SQLException e) {
        return new DataAccessException("No se pudo completar la administración del catálogo.", e);
    }
}
