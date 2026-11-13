// Declara JavaFX y permite que FXMLLoader acceda al controlador de la pantalla pública.
module cr.ac.una.QueueDisplayScreen {
    requires javafx.controls;
    requires javafx.fxml;

    opens cr.ac.una.QueueDisplayScreen.controller to javafx.fxml;
    exports cr.ac.una.QueueDisplayScreen;
}
