package cr.ac.una.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

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

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
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
