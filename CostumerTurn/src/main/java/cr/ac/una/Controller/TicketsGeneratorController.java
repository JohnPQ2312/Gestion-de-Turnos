/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cr.ac.una.Controller;

import cr.ac.una.model.Service;
import cr.ac.una.repository.InMemoryServiceRepository;
import cr.ac.una.model.Ticket;
import cr.ac.una.repository.InMemoryTicketRepository;
import cr.ac.una.service.TicketService;
import javafx.scene.control.Alert;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;

/**
 *
 * @author User
 */
public class TicketsGeneratorController {

    private TicketService ticketService;
    private Runnable onTicketGenerated = () -> {
    };

    private final InMemoryServiceRepository serviceRepository
            = new InMemoryServiceRepository();

    public void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public void setOnTicketGenerated(Runnable onTicketGenerated) {
        this.onTicketGenerated = onTicketGenerated;
    }

    @FXML
    private ComboBox<Service> servicesCombo;
    @FXML
    private Button genTurnButton;
    @FXML
    private ToggleButton priorityButton;

    @FXML
    private void generateTicket(ActionEvent event) {
        Service selectedService = servicesCombo.getValue();
        boolean priority = priorityButton.isSelected();

        try {
            Ticket generatedTicket
                    = ticketService.generateTicket(selectedService, priority);

            onTicketGenerated.run();

            showTicketConfirmation(generatedTicket);

        } catch (IllegalArgumentException exception) {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.initOwner(genTurnButton.getScene().getWindow());
            errorAlert.setTitle("No se pudo generar el turno");
            errorAlert.setHeaderText(null);
            errorAlert.setContentText(exception.getMessage());
            errorAlert.showAndWait();
        }
    }

    private void showTicketConfirmation(Ticket ticket) {
        String attentionType
                = ticket.isPriority() ? "Preferencial" : "Regular";

        Alert confirmationAlert = new Alert(Alert.AlertType.INFORMATION);
        confirmationAlert.initOwner(genTurnButton.getScene().getWindow());
        confirmationAlert.setTitle("Turno generado");
        confirmationAlert.setHeaderText("Su turno es " + ticket.getVisualCode());

        confirmationAlert.setContentText(
                "Servicio: " + ticket.getService().getName()
                + "\nAtención: " + attentionType
                + "\n\nEspere el llamado de su turno."
        );

        confirmationAlert.showAndWait();
    }

    @FXML
    private void initialize() {
        servicesCombo.getItems().setAll(serviceRepository.findAll());
    }

}
