package cr.ac.una.costumerturn.controller;

import cr.ac.una.costumerturn.model.ServiceOption;
import cr.ac.una.costumerturn.model.TicketConfirmation;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;

public class TicketsGeneratorController {

    @FunctionalInterface
    public interface ServicesRequest {

        List<ServiceOption> list() throws IOException;
    }

    @FunctionalInterface
    public interface TicketRequest {

        TicketConfirmation generate(String serviceCode, boolean priority)
                throws IOException;
    }

    private ServicesRequest servicesRequest;
    private TicketRequest ticketRequest;
    private Task<?> currentTask;
    private boolean catalogLoaded;
    private boolean busy;
    private boolean closed;

    @FXML
    private ComboBox<ServiceOption> servicesCombo;
    @FXML
    private Button genTurnButton;
    @FXML
    private ToggleButton priorityButton;

    public void configure(ServicesRequest servicesRequest, TicketRequest ticketRequest) {
        Objects.requireNonNull(servicesRequest, "La consulta de servicios es obligatoria.");
        Objects.requireNonNull(ticketRequest, "La solicitud de turnos es obligatoria.");
        if (busy || closed) {
            throw new IllegalStateException("La pantalla no se puede configurar en este momento.");
        }
        this.servicesRequest = servicesRequest;
        this.ticketRequest = ticketRequest;
        loadServices();
    }

    private void loadServices() {
        catalogLoaded = false;
        servicesCombo.getItems().clear();
        servicesCombo.setPromptText("Consultando servicios...");
        genTurnButton.setText("Conectando...");

        Task<List<ServiceOption>> task = new Task<>() {
            @Override
            protected List<ServiceOption> call() throws IOException {
                return servicesRequest.list();
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }
            catalogLoaded = true;
            servicesCombo.getItems().setAll(task.getValue());
            servicesCombo.setPromptText(task.getValue().isEmpty()
                    ? "No hay servicios disponibles" : "Tipo de servicio");
            genTurnButton.setText("Generar turno");
            setBusy(false);
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            servicesCombo.setPromptText("Sin conexión al servidor");
            genTurnButton.setText("Reconectar");
            setBusy(false);
            showFailure(
                    "No se pudo consultar el catálogo. Compruebe que CoreServer esté iniciado.",
                    task.getException()
            );
        });

        startTask(task);
    }

    @FXML
    private void generateTicket(ActionEvent event) {
        if (busy || closed || ticketRequest == null) {
            return;
        }
        if (!catalogLoaded) {
            loadServices();
            return;
        }

        ServiceOption selectedService = servicesCombo.getValue();
        if (selectedService == null) {
            showError("Debe seleccionar un servicio.");
            return;
        }
        boolean priority = priorityButton.isSelected();
        genTurnButton.setText("Generando...");

        Task<TicketConfirmation> task = new Task<>() {
            @Override
            protected TicketConfirmation call() throws IOException {
                return ticketRequest.generate(selectedService.code(), priority);
            }
        };

        task.setOnSucceeded(finished -> {
            if (closed) {
                return;
            }
            genTurnButton.setText("Generar turno");
            setBusy(false);
            showTicketConfirmation(task.getValue());
        });

        task.setOnFailed(failed -> {
            if (closed) {
                return;
            }
            genTurnButton.setText("Generar turno");
            setBusy(false);
            showFailure(
                    "No se recibió la confirmación del turno. Compruebe la conexión "
                    + "con CoreServer antes de volver a solicitarlo.",
                    task.getException()
            );
        });

        startTask(task);
    }

    private void startTask(Task<?> task) {
        currentTask = task;
        setBusy(true);
        Thread.ofVirtual().name("costumerturn-request").start(task);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        boolean noServices = servicesCombo.getItems().isEmpty();
        servicesCombo.setDisable(busy || !catalogLoaded || noServices);
        priorityButton.setDisable(busy || !catalogLoaded || noServices);
        genTurnButton.setDisable(busy || (catalogLoaded && noServices));
    }

    private void showFailure(String message, Throwable error) {
        System.err.println(message + " Detalle: " + error);
        if ((error instanceof IllegalArgumentException
                || error instanceof IllegalStateException)
                && error.getMessage() != null) {
            showError(error.getMessage());
        } else {
            showError(message);
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(genTurnButton.getScene().getWindow());
        alert.setTitle("Generador de turnos");
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

    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }
}
