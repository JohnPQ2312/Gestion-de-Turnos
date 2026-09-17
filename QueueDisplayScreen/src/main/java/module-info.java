module cr.ac.una.QueueDisplayScreen {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;

    opens cr.ac.una.QueueDisplayScreen to javafx.fxml;
    exports cr.ac.una.QueueDisplayScreen;
}
