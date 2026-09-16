module sistematurnos.turnos.pantalla {
    requires javafx.controls;
    requires javafx.fxml;

    opens sistematurnos.turnos.pantalla to javafx.fxml;
    exports sistematurnos.turnos.pantalla;
}
