// Declara JavaFX y permite que FXMLLoader cree los controladores de las vistas del operador.
module cr.ac.una.operatorsystem {
    requires javafx.controls;
    requires javafx.fxml;

    opens cr.ac.una.operatorsystem.controller to javafx.fxml;
    exports cr.ac.una.operatorsystem;
}
