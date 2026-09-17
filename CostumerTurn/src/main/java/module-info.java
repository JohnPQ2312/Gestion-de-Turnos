module cr.ac.una.costumerturn {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;

    opens cr.ac.una.costumerturn to javafx.fxml;
    exports cr.ac.una.costumerturn;
}
