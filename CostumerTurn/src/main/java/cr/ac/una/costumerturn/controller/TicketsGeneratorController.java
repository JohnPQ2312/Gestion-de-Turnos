package cr.ac.una.costumerturn.controller;

import cr.ac.una.costumerturn.model.ServiceOption;
import cr.ac.una.costumerturn.model.TicketConfirmation;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class TicketsGeneratorController {

    @FunctionalInterface
    public interface ServicesRequest {
        List<ServiceOption> list() throws IOException;
    }

    @FunctionalInterface
    public interface TicketRequest {
        TicketConfirmation generate(String serviceCode, boolean priority) throws IOException;
    }

    private ServicesRequest servicesRequest;
    private TicketRequest ticketRequest;
    private Task<?> currentTask;
    private boolean catalogLoaded;
    private boolean busy;
    private boolean closed;
    private boolean confirmationShowing;

    @FXML private ToggleGroup servicesGroup;
    @FXML private VBox serviceOptions;
    @FXML private CheckBox priorityCheck;
    @FXML private Button genTurnButton;
    @FXML private Button newPersonButton;
    @FXML private Label selectedServiceLabel;
    @FXML private Label attentionLabel;
    @FXML private Label statusLabel;
    @FXML private Label errorLabel;
    @FXML private Label ticketCodeLabel;
    @FXML private Label ticketServiceLabel;
    @FXML private Label ticketAttentionLabel;
    @FXML private BorderPane formView;
    @FXML private ScrollPane confirmationView;

    @FXML
    private void initialize() {
        servicesGroup.selectedToggleProperty().addListener((observable, previous, selected) -> {
            show(errorLabel, false);
            updateSelection();
        });
        priorityCheck.selectedProperty().addListener((observable, previous, selected) -> updateSelection());
        updateSelection();
        setBusy(false);
    }

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
        servicesGroup.selectToggle(null);
        servicesGroup.getToggles().clear();
        serviceOptions.getChildren().clear();
        show(errorLabel, false);
        showStatus("Consultando servicios disponibles…", false);
        genTurnButton.setText("Conectando…");

        Task<List<ServiceOption>> task = new Task<>() {
            @Override
            protected List<ServiceOption> call() throws IOException {
                return List.copyOf(servicesRequest.list());
            }
        };
        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }
            catalogLoaded = true;
            for (ServiceOption service : task.getValue()) {
                RadioButton choice = new RadioButton(service.name());
                choice.setUserData(service);
                choice.setToggleGroup(servicesGroup);
                choice.setWrapText(true);
                choice.setMinHeight(72);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.getStyleClass().add("service-choice");
                serviceOptions.getChildren().add(choice);
            }
            if (task.getValue().isEmpty()) {
                showStatus("No hay servicios disponibles en este momento. Consulte al personal de atención.", false);
            } else {
                show(statusLabel, false);
            }
            genTurnButton.setText(task.getValue().isEmpty() ? "Actualizar servicios" : "Generar turno");
            setBusy(false);
        });
        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            genTurnButton.setText("Reconectar");
            setBusy(false);
            showFailure("No se pudieron consultar los servicios. Consulte al personal o pulse Reconectar.", task.getException());
        });
        startTask(task);
    }

    @FXML
    private void generateTicket(ActionEvent event) {
        if (busy || closed || confirmationShowing || ticketRequest == null) {
            return;
        }
        if (!catalogLoaded || servicesGroup.getToggles().isEmpty()) {
            loadServices();
            return;
        }
        ServiceOption selected = selectedService();
        if (selected == null) {
            errorLabel.setText("Seleccione un servicio para continuar.");
            show(errorLabel, true);
            if (!serviceOptions.getChildren().isEmpty()) {
                serviceOptions.getChildren().get(0).requestFocus();
            }
            return;
        }
        boolean priority = priorityCheck.isSelected();
        show(errorLabel, false);
        genTurnButton.setText("Generando…");
        Task<TicketConfirmation> task = new Task<>() {
            @Override
            protected TicketConfirmation call() throws IOException {
                return Objects.requireNonNull(ticketRequest.generate(selected.code(), priority),
                        "La confirmación del turno es obligatoria.");
            }
        };
        task.setOnSucceeded(finished -> {
            if (closed) {
                return;
            }
            genTurnButton.setText("Generar turno");
            showTicketConfirmation(task.getValue());
            setBusy(false);
        });
        task.setOnFailed(failed -> {
            if (closed) {
                return;
            }
            genTurnButton.setText("Generar turno");
            setBusy(false);
            showFailure("No se recibió la confirmación del turno. Consulte al personal para comprobar "
                    + "si fue generado antes de volver a solicitarlo.", task.getException());
        });
        startTask(task);
    }

    private ServiceOption selectedService() {
        Toggle selected = servicesGroup.getSelectedToggle();
        return selected == null ? null : (ServiceOption) selected.getUserData();
    }

    private void updateSelection() {
        ServiceOption selected = selectedService();
        selectedServiceLabel.setText(selected == null ? "Elija un servicio" : selected.name());
        attentionLabel.setText(priorityCheck.isSelected() ? "Atención preferencial" : "Atención regular");
    }

    private void showTicketConfirmation(TicketConfirmation ticket) {
        confirmationShowing = true;
        ticketCodeLabel.setText(ticket.visualCode());
        int size = ticket.visualCode().length() > 12 ? 28 : ticket.visualCode().length() > 8 ? 48 : 96;
        ticketCodeLabel.setStyle("-fx-font-size: " + size + "px;");
        ticketServiceLabel.setText(ticket.serviceName());
        ticketAttentionLabel.setText(ticket.priority() ? "Atención preferencial" : "Atención regular");
        show(formView, false);
        show(confirmationView, true);
        newPersonButton.requestFocus();
    }

    @FXML
    private void finishConfirmation() {
        if (!confirmationShowing || busy || closed) {
            return;
        }
        servicesGroup.selectToggle(null);
        priorityCheck.setSelected(false);
        ticketCodeLabel.setText("");
        ticketServiceLabel.setText("");
        ticketAttentionLabel.setText("");
        show(statusLabel, false);
        show(errorLabel, false);
        confirmationShowing = false;
        show(confirmationView, false);
        show(formView, true);
        setBusy(false);
        if (!serviceOptions.getChildren().isEmpty()) {
            serviceOptions.getChildren().get(0).requestFocus();
        }
    }

    private void startTask(Task<?> task) {
        currentTask = task;
        setBusy(true);
        Thread.ofVirtual().name("costumerturn-request").start(task);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        boolean noServices = servicesGroup.getToggles().isEmpty();
        serviceOptions.setDisable(closed || busy || !catalogLoaded || confirmationShowing);
        priorityCheck.setDisable(closed || busy || !catalogLoaded || noServices || confirmationShowing);
        // Un catálogo vacío permite consultar de nuevo sin reiniciar la terminal.
        genTurnButton.setDisable(closed || busy || confirmationShowing || ticketRequest == null);
        genTurnButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("busy"), busy);
    }

    private void showFailure(String message, Throwable error) {
        System.err.println(message + " Detalle: " + error);
        if ((error instanceof IllegalArgumentException || error instanceof IllegalStateException)
                && error.getMessage() != null) {
            showStatus(error.getMessage(), true);
        } else {
            showStatus(message, true);
        }
    }

    private void showStatus(String message, boolean warning) {
        statusLabel.setText(message);
        statusLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("warning"), warning);
        show(statusLabel, true);
    }

    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        setBusy(false);
    }
}
