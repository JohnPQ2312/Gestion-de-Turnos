package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.CatalogRequests;
import java.util.function.Consumer;
import javafx.fxml.FXML;

// Agrupa Servicios y Ventanillas y configura sus controladores con las mismas solicitudes y
// sesión.
public class CatalogController {
    @FXML private ServicesController servicesController;
    @FXML private WindowsController windowsController;

    public void configure(CatalogRequests requests, Consumer<String> sessionEnded) {
        servicesController.configure(requests, sessionEnded);
        windowsController.configure(requests, sessionEnded);
    }

    public void close() {
        servicesController.close();
        windowsController.close();
    }
}
