module sistematurnos.turnos.cliente {
    requires javafx.controls;
    requires javafx.fxml;

    opens sistematurnos.turnos.cliente to javafx.fxml;
    exports sistematurnos.turnos.cliente;
}
