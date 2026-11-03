package cr.ac.una.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseConnectionCheck {

    public static void main(String[] args) {
        DriverManager.setLoginTimeout(5);

        try (Connection connection
                = new DatabaseConnectionFactory().openConnection()) {

            DatabaseMetaData metadata = connection.getMetaData();

            System.out.println("Conexión exitosa.");
            System.out.println(
                    "Motor: " + metadata.getDatabaseProductName()
                    + " " + metadata.getDatabaseProductVersion()
            );
            System.out.println(
                    "Base de datos: " + connection.getCatalog()
            );

            System.out.println("\nTablas existentes:");

            try (ResultSet tables = metadata.getTables(
                    connection.getCatalog(),
                    null,
                    "%",
                    new String[]{"TABLE"}
            )) {
                boolean found = false;

                while (tables.next()) {
                    System.out.println(
                            "- " + tables.getString("TABLE_NAME")
                    );
                    found = true;
                }

                if (!found) {
                    System.out.println("Todavía no hay tablas.");
                }
            }

        } catch (SQLException exception) {
            System.err.println(
                    "Falló la comprobación: "
                    + exception.getMessage()
            );
        }
    }
}
