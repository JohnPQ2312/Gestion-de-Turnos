package cr.ac.una.repository;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcPeopleAdministrationRepository implements PeopleAdministrationRepository {
    private static final String ACCOUNT_SQL = """
            SELECT o.operator_id, o.operator_name, o.operator_username,
                   o.operator_role, o.operator_state, c.password_hash
            FROM `operator` o LEFT JOIN operator_credential c ON c.operator_id = o.operator_id
            """;
    private final DatabaseConnectionFactory factory;

    public JdbcPeopleAdministrationRepository(DatabaseConnectionFactory factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    @Override
    public List<ManagedOperator> listOperators(AdministrationActor actor) {
        try (Connection c = factory.openConnection()) {
            JdbcAdministrationAccess.require(c, actor, false);
            String sql = """
                    SELECT o.operator_id, o.operator_name, o.operator_username,
                           o.operator_role, o.operator_state,
                           EXISTS(SELECT 1 FROM operator_credential c WHERE c.operator_id=o.operator_id) AS credential,
                           EXISTS(SELECT 1 FROM operator_window_assignment a
                                  WHERE a.operator_id=o.operator_id AND a.ended_at IS NULL) AS assigned
                    FROM `operator` o ORDER BY o.operator_id
                    """;
            try (PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
                List<ManagedOperator> rows = new ArrayList<>();
                while (r.next()) {
                    rows.add(new ManagedOperator(r.getInt(1), r.getString(2), r.getString(3),
                            UserRole.fromDatabase(r.getString(4)), "ACTIVO".equals(r.getString(5)),
                            r.getBoolean(6), r.getBoolean(7)));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    @Override
    public int createOperator(AdministrationActor actor, String name, String username, UserRole role, String hash) {
        return write(actor, c -> {
            int id;
            try (PreparedStatement s = c.prepareStatement("""
                    INSERT INTO `operator`(operator_name,operator_username,operator_role,operator_state)
                    VALUES (?,?,?,'ACTIVO')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1, name);
                s.setString(2, username);
                s.setString(3, role.name());
                s.executeUpdate();
                try (ResultSet r = s.getGeneratedKeys()) {
                    if (!r.next()) throw new SQLException("No se recibió el identificador del funcionario.");
                    id = r.getInt(1);
                }
            }
            savePassword(c, id, hash);
            return id;
        });
    }

    @Override
    public AccountChange updateOperator(AdministrationActor actor, int id, String name,
            String username, UserRole role, boolean active) {
        return write(actor, c -> {
            OperatorAccount before = account(c, id, true);
            boolean changesAccess = before.role() != role || before.active() != active;
            if (changesAccess && hasOpenAssignment(c, id)) {
                throw new IllegalStateException("Cierre primero la asignación del funcionario antes de cambiar su rol o estado.");
            }
            if (!active || role != UserRole.ADMIN) requireAnotherAdmin(c, before);
            try (PreparedStatement s = c.prepareStatement("""
                    UPDATE `operator` SET operator_name=?,operator_username=?,operator_role=?,operator_state=?
                    WHERE operator_id=?
                    """)) {
                s.setString(1, name);
                s.setString(2, username);
                s.setString(3, role.name());
                s.setString(4, active ? "ACTIVO" : "INACTIVO");
                s.setInt(5, id);
                s.executeUpdate();
            }
            return new AccountChange(before, new OperatorAccount(id, username, name, role, active, before.passwordHash()));
        });
    }

    @Override
    public AccountChange resetPassword(AdministrationActor actor, int id, String hash) {
        return write(actor, c -> {
            OperatorAccount before = account(c, id, true);
            savePassword(c, id, hash);
            return new AccountChange(before, new OperatorAccount(id, before.username(), before.name(), before.role(), before.active(), hash));
        });
    }

    @Override
    public List<ManagedAppUser> listUsers(AdministrationActor actor) {
        try (Connection c = factory.openConnection()) {
            JdbcAdministrationAccess.require(c, actor, false);
            try (PreparedStatement s = c.prepareStatement("SELECT user_id,username,user_state FROM app_user ORDER BY user_id");
                    ResultSet r = s.executeQuery()) {
                List<ManagedAppUser> rows = new ArrayList<>();
                while (r.next()) rows.add(new ManagedAppUser(r.getInt(1), r.getString(2), "ACTIVO".equals(r.getString(3))));
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    @Override
    public int createUser(AdministrationActor actor, String username) {
        return write(actor, c -> {
            requireUniqueUser(c, 0, username);
            try (PreparedStatement s = c.prepareStatement("INSERT INTO app_user(username,user_state) VALUES (?,'ACTIVO')",
                    Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1, username);
                s.executeUpdate();
                try (ResultSet r = s.getGeneratedKeys()) {
                    if (!r.next()) throw new SQLException("No se recibió el identificador del usuario.");
                    return r.getInt(1);
                }
            }
        });
    }

    @Override
    public void updateUser(AdministrationActor actor, int id, String username, boolean active) {
        write(actor, c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE")) {
                s.setInt(1, id);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("El usuario solicitado no existe.");
                }
            }
            requireUniqueUser(c, id, username);
            if (!active) {
                try (PreparedStatement s = c.prepareStatement("""
                        SELECT ticket_id FROM ticket WHERE user_id=?
                        AND ticket_state IN ('EN_ESPERA','LLAMADO','EN_ATENCION') LIMIT 1
                        """)) {
                    s.setInt(1, id);
                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) throw new IllegalStateException("El usuario tiene turnos pendientes; finalícelos antes de darlo de baja.");
                    }
                }
            }
            try (PreparedStatement s = c.prepareStatement("UPDATE app_user SET username=?,user_state=? WHERE user_id=?")) {
                s.setString(1, username);
                s.setString(2, active ? "ACTIVO" : "INACTIVO");
                s.setInt(3, id);
                s.executeUpdate();
            }
            return null;
        });
    }

    private void requireUniqueUser(Connection c, int id, String username) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT user_id FROM app_user WHERE username=? AND user_id<>? LIMIT 1")) {
            s.setString(1, username);
            s.setInt(2, id);
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) throw new IllegalArgumentException("Ya existe un usuario con ese nombre.");
            }
        }
    }

    private static OperatorAccount account(Connection c, int id, boolean lock) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(ACCOUNT_SQL + " WHERE o.operator_id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("El funcionario solicitado no existe.");
                return new OperatorAccount(r.getInt(1), r.getString(3), r.getString(2),
                        UserRole.fromDatabase(r.getString(4)), "ACTIVO".equals(r.getString(5)), r.getString(6));
            }
        }
    }

    private static boolean hasOpenAssignment(Connection c, int id) throws SQLException {
        // No se toma el bloqueo inverso operador -> asignación usado por el llamado.
        // El bloque de apertura/cierre deberá coordinarse con estos cambios.
        try (PreparedStatement s = c.prepareStatement("SELECT assignment_id FROM operator_window_assignment WHERE operator_id=? AND ended_at IS NULL LIMIT 1")) {
            s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    private static void requireAnotherAdmin(Connection c, OperatorAccount before) throws SQLException {
        if (!before.active() || before.role() != UserRole.ADMIN || !before.hasCredential()) return;
        try (PreparedStatement s = c.prepareStatement("""
                SELECT COUNT(*) FROM `operator` o JOIN operator_credential c ON c.operator_id=o.operator_id
                WHERE o.operator_role='ADMIN' AND o.operator_state='ACTIVO' AND o.operator_id<>?
                """)) {
            s.setInt(1, before.operatorId());
            try (ResultSet r = s.executeQuery()) {
                r.next();
                if (r.getInt(1) == 0) throw new IllegalStateException("Debe conservarse al menos un administrador activo con credencial.");
            }
        }
    }

    private static void savePassword(Connection c, int id, String hash) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO operator_credential(operator_id,password_hash) VALUES (?,?)
                ON DUPLICATE KEY UPDATE password_hash=?
                """)) {
            s.setInt(1, id);
            s.setString(2, hash);
            s.setString(3, hash);
            s.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface Write<T> { T execute(Connection c) throws SQLException; }

    private <T> T write(AdministrationActor actor, Write<T> action) {
        try (Connection c = factory.openConnection()) {
            c.setAutoCommit(false);
            try {
                // Serializa las escrituras administrativas y protege al último ADMIN.
                // No bloquea asignaciones: el llamado toma esas filas antes del operador.
                try (PreparedStatement s = c.prepareStatement("SELECT operator_id FROM `operator` WHERE operator_role='ADMIN' ORDER BY operator_id FOR UPDATE");
                        ResultSet r = s.executeQuery()) {
                    while (r.next()) { r.getInt(1); }
                }
                JdbcAdministrationAccess.require(c, actor, true);
                T result = action.execute(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("Ya existe un funcionario con ese nombre de acceso.");
            throw failure(e);
        }
    }

    private static DataAccessException failure(SQLException e) {
        return new DataAccessException("No se pudo completar la administración de personas.", e);
    }
}
