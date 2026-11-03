module cr.ac.una.costumerturn {
    requires javafx.controls;
    requires javafx.fxml;

    opens cr.ac.una.costumerturn.controller to javafx.fxml;
    exports cr.ac.una.costumerturn;
}
