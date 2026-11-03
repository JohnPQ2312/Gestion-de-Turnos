package cr.ac.una.costumerturn.controller;

import cr.ac.una.costumerturn.model.ServiceOption;
import cr.ac.una.costumerturn.model.TicketConfirmation;
import java.util.List;
import java.util.Objects;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;

public class TicketsGeneratorController {

    @FunctionalInterface
    public interface TicketRequest {
        TicketConfirmation generate(String serviceCode, boolean priority);
    }

    private TicketRequest ticketRequest;

    @FXML
    private ComboBox<ServiceOption> servicesCombo;
    @FXML
    private Button genTurnButton;
    @FXML
    private ToggleButton priorityButton;

    public void configure(List<ServiceOption> services, TicketRequest ticketRequest) {
        Objects.requireNonNull(services, "La lista de servicios es obligatoria.");
        this.ticketRequest = Objects.requireNonNull(
                ticketRequest, "La solicitud de turnos es obligatoria."
        );
        servicesCombo.getItems().setAll(services);
        servicesCombo.setPromptText("Tipo de servicio");
        servicesCombo.setDisable(services.isEmpty());
        genTurnButton.setDisable(services.isEmpty());
    }

    @FXML
    private void generateTicket(ActionEvent event) {
        if (ticketRequest == null) {
            return;
        }
        ServiceOption selectedService = servicesCombo.getValue();
        if (selectedService == null) {
            showError("Debe seleccionar un servicio.");
            return;
        }
        try {
            TicketConfirmation generatedTicket = ticketRequest.generate(
                    selectedService.code(), priorityButton.isSelected()
            );
            showTicketConfirmation(generatedTicket);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(genTurnButton.getScene().getWindow());
        alert.setTitle("No se pudo generar el turno");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showTicketConfirmation(TicketConfirmation ticket) {
        String attentionType = ticket.priority() ? "Preferencial" : "Regular";
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(genTurnButton.getScene().getWindow());
        alert.setTitle("Turno generado");
        alert.setHeaderText("Su turno es " + ticket.visualCode());
        alert.setContentText(
                "Servicio: " + ticket.serviceName()
                + "\nAtención: " + attentionType
                + "\n\nEspere el llamado de su turno."
        );
        alert.showAndWait();
    }
}
