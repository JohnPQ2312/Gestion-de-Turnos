package cr.ac.una.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Carga db.properties y abre una conexión JDBC por cada operación que la solicita.
// Registra el tiempo de apertura y los fallos para medir el costo de conexión por solicitud.
public final class DatabaseConnectionFactory {

    private final String url;
    private final String username;
    private final String password;

    public DatabaseConnectionFactory() throws SQLException {
        Properties properties = new Properties();

        try (InputStream input = DatabaseConnectionFactory.class
                .getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (input == null) {
                throw new SQLException(
                        "No se encontró db.properties en src/main/resources."
                );
            }

            properties.load(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            );

        } catch (IOException exception) {
            throw new SQLException(
                    "No se pudo leer la configuración de MySQL.",
                    exception
            );
        }

        this.url = requireProperty(properties, "db.url");
        this.username = requireProperty(properties, "db.usuario");

        if (!properties.containsKey("db.clave")) {
            throw new SQLException(
                    "Falta la propiedad db.clave en db.properties."
            );
        }

        this.password = properties.getProperty("db.clave");
    }

       // Abre una conexión nueva y registra su tiempo, tanto si conecta como si falla.
       public Connection openConnection() throws SQLException {
        long start = System.nanoTime();
        try {
            Connection connection
                    = DriverManager.getConnection(url, username, password);
            ConnectionMetrics.recordSuccess(System.nanoTime() - start);
            return connection;
        } catch (SQLException | RuntimeException exception) {
            ConnectionMetrics.recordFailure(System.nanoTime() - start);
            throw exception;
        }
    }

    private static String requireProperty(
            Properties properties,
            String name
    ) throws SQLException {
        String value = properties.getProperty(name);

        if (value == null || value.isBlank()) {
            throw new SQLException(
                    "Falta configurar " + name + " en db.properties."
            );
        }

        return value.trim();
    }
}
