package cr.ac.una.repository;

import cr.ac.una.exception.AccessDeniedException;
import cr.ac.una.exception.AccessDeniedException.Reason;
import cr.ac.una.model.AdministrationActor;
import cr.ac.una.security.PasswordHasher;
import java.sql.*;
import java.util.Objects;

// Revalida en la conexión actual el estado, rol y credencial del administrador.
// La identidad recibida debe coincidir con la cuenta que sigue vigente en MySQL.
final class JdbcAdministrationAccess {
    private JdbcAdministrationAccess() { }

    // Comprueba que el administrador siga activo y conserve el rol y la credencial de la sesión.
    static void require(Connection c, AdministrationActor actor, boolean lock) throws SQLException {
        Objects.requireNonNull(actor, "La identidad administrativa es obligatoria.");
        String sql = """
                SELECT o.operator_role,o.operator_state,c.password_hash FROM `operator` o
                LEFT JOIN operator_credential c ON c.operator_id=o.operator_id WHERE o.operator_id=?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, actor.operatorId());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next() || !"ADMIN".equals(r.getString(1)) || !"ACTIVO".equals(r.getString(2))
                        || r.getString(3) == null || !PasswordHasher.fingerprint(r.getString(3)).equals(actor.credentialFingerprint())) {
                    throw new AccessDeniedException(Reason.FORBIDDEN, "La cuenta administrativa cambió. Inicie sesión de nuevo.");
                }
            }
        }
    }
}
