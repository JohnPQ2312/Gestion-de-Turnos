package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.CatalogRequests;
import java.util.function.Consumer;
import javafx.fxml.FXML;

// Contenedor de la administración del catálogo; cada pestaña tiene su propio controlador.
public class CatalogController {
    @FXML private ServicesController servicesController;

    public void configure(CatalogRequests requests, Consumer<String> sessionEnded) {
        servicesController.configure(requests, sessionEnded);
    }

    public void close() {
        servicesController.close();
    }
}
