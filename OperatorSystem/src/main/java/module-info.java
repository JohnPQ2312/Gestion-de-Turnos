module cr.ac.una.operatorsystem {
    requires javafx.controls;
    requires javafx.fxml;

    opens cr.ac.una.operatorsystem to javafx.fxml;
    exports cr.ac.una.operatorsystem;
}
