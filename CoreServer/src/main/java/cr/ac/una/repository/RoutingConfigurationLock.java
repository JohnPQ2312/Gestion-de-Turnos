package cr.ac.una.repository;

import java.sql.*;

// Coordina cambios de habilitaciones, asignaciones y catálogos mediante GET_LOCK por base.
// Los bloqueos de filas y las restricciones SQL siguen protegiendo cada operación.
final class RoutingConfigurationLock implements AutoCloseable {
    private final Connection connection;

    RoutingConfigurationLock(Connection connection) throws SQLException {
        this.connection = connection;
        try (PreparedStatement s = connection.prepareStatement("SELECT GET_LOCK(CONCAT('turnos.routing.',MD5(DATABASE())),5)");
                ResultSet r = s.executeQuery()) {
            if (!r.next() || r.getInt(1) != 1) {
                throw new IllegalStateException("Hay otro cambio de configuración administrativa en curso. Actualice antes de volver a intentarlo.");
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
