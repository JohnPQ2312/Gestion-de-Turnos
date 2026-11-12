package cr.ac.una.repository;

import java.sql.*;

// Serializa cambios de habilitaciones y asignaciones por base, incluso entre conexiones.
// No sustituye los bloqueos de filas ni las restricciones SQL del llamado.
final class RoutingConfigurationLock implements AutoCloseable {
    private final Connection connection;

    RoutingConfigurationLock(Connection connection) throws SQLException {
        this.connection = connection;
        try (PreparedStatement s = connection.prepareStatement("SELECT GET_LOCK(CONCAT('turnos.routing.',MD5(DATABASE())),5)");
                ResultSet r = s.executeQuery()) {
            if (!r.next() || r.getInt(1) != 1) {
                throw new IllegalStateException("Hay otro cambio de habilitaciones o asignaciones en curso. Actualice antes de volver a intentarlo.");
            }
        }
    }

    @Override
    public void close() throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT RELEASE_LOCK(CONCAT('turnos.routing.',MD5(DATABASE())))");
                ResultSet r = s.executeQuery()) {
            if (!r.next() || r.getInt(1) != 1) throw new SQLException("No se confirmó la liberación del cambio de configuración.");
        }
    }
}
