package cr.ac.una.costumerturn.controller;

import cr.ac.una.costumerturn.model.ServiceOption;
import cr.ac.una.costumerturn.model.TicketConfirmation;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
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

// Consulta servicios y solicita turnos sin bloquear el hilo de JavaFX.
// Muestra la confirmación recibida y prepara el formulario para la siguiente persona.
// El catálogo se puede actualizar sin reiniciar: con el botón Actualizar servicios, al terminar
// con una persona y tras un rechazo del servidor. La actualización nunca coincide con una emisión
// ni con la confirmación abierta, y conserva la lista actual si la consulta falla.
public class TicketsGeneratorController {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final String SELECT_HINT = "Seleccione una opción.";

    @FunctionalInterface
    public interface ServicesRequest {
        List<ServiceOption> list() throws IOException;
    }

    @FunctionalInterface
    public interface TicketRequest {
        TicketConfirmation generate(String serviceCode, boolean priority) throws IOException;
    }

    // Dónde devolver el foco del teclado cuando termina una consulta del catálogo.
    private enum Focus { NONE, LIST, REFRESH }

    private ServicesRequest servicesRequest;
    private TicketRequest ticketRequest;
    private Task<?> currentTask;
    private List<ServiceOption> currentServices = List.of();
    private boolean catalogLoaded;
    private boolean busy;
    private boolean closed;
    private boolean confirmationShowing;

    @FXML private ToggleGroup servicesGroup;
    @FXML private VBox serviceOptions;
    @FXML private ScrollPane serviceScroll;
    @FXML private Label serviceHelperLabel;
    @FXML private Button refreshButton;
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
        requestCatalog(null, Focus.NONE);
    }

    // Actualización pedida por la persona. Se ignora durante una consulta o una emisión y
    // mientras se muestra la confirmación, que nunca se reemplaza.
    @FXML
    private void refreshServices() {
        if (busy || closed || confirmationShowing || servicesRequest == null) {
            return;
        }
        requestCatalog(null, Focus.REFRESH);
    }

    // Consulta el catálogo en segundo plano. Sin catálogo previo (primera carga o reconexión)
    // vacía la lista; con uno cargado lo conserva visible hasta recibir el nuevo y, si la consulta
    // falla, sigue ofreciéndolo con un aviso. notice es un mensaje que debe mantenerse a la vista
    // después de actualizar (por ejemplo, el rechazo de un servicio dado de baja).
    private void requestCatalog(String notice, Focus requestedFocus) {
        boolean keepList = catalogLoaded;
        Focus focus = requestedFocus != Focus.NONE ? requestedFocus
                : refreshButton.isFocused() ? Focus.REFRESH
                : serviceOptions.getChildren().stream().anyMatch(Node::isFocused) ? Focus.LIST : Focus.NONE;
        if (keepList) {
            refreshButton.setText("Actualizando…");
            genTurnButton.setText("Actualizando…");
        } else {
            catalogLoaded = false;
            servicesGroup.selectToggle(null);
            servicesGroup.getToggles().clear();
            serviceOptions.getChildren().clear();
            currentServices = List.of();
            show(errorLabel, false);
            showStatus("Consultando servicios disponibles…", false);
            genTurnButton.setText("Conectando…");
        }

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
            applyCatalog(task.getValue(), notice);
            refreshButton.setText("Actualizar servicios");
            genTurnButton.setText(currentServices.isEmpty() ? "Actualizar servicios" : "Generar turno");
            setBusy(false);
            restoreFocus(focus);
        });
        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            refreshButton.setText("Actualizar servicios");
            if (keepList) {
                genTurnButton.setText(currentServices.isEmpty() ? "Actualizar servicios" : "Generar turno");
                setBusy(false);
                System.err.println("No se pudo actualizar el catálogo. Detalle: " + task.getException());
                if (notice == null) {
                    showStatus("No se pudo actualizar la lista de servicios. Se muestra la última lista "
                            + "conocida; pulse Actualizar servicios para reintentar.", true);
                }
                restoreFocus(focus);
            } else {
                genTurnButton.setText("Reconectar");
                setBusy(false);
                showFailure("No se pudieron consultar los servicios. Consulte al personal o pulse Reconectar.", task.getException());
            }
        });
        startTask(task);
    }

    // Reemplaza la lista solo si cambió, conserva el servicio elegido si sigue existiendo y avisa
    // cuando desapareció. La prioridad marcada no se modifica.
    private void applyCatalog(List<ServiceOption> fresh, String notice) {
        ServiceOption previous = selectedService();
        boolean vanished = false;
        if (!catalogLoaded || !fresh.equals(currentServices)) {
            rebuildChoices(fresh);
            if (previous != null) {
                Toggle same = servicesGroup.getToggles().stream()
                        .filter(toggle -> ((ServiceOption) toggle.getUserData()).code().equals(previous.code()))
                        .findFirst().orElse(null);
                if (same != null) {
                    servicesGroup.selectToggle(same);
                    Node choice = (Node) same;
                    Platform.runLater(() -> scrollToVisible(choice));
                } else {
                    vanished = true;
                }
            }
        }
        currentServices = fresh;
        catalogLoaded = true;
        serviceHelperLabel.setText("Actualizado a las " + HOUR.format(LocalTime.now()) + ". " + SELECT_HINT);

        if (fresh.isEmpty()) {
            showStatus("No hay servicios disponibles en este momento. Consulte al personal de atención.", false);
        } else if (notice != null) {
            showStatus(notice, true);
        } else if (vanished) {
            showStatus("El servicio «" + previous.name() + "» ya no está disponible. Elija otro servicio.", true);
        } else {
            show(statusLabel, false);
        }
    }

    private void rebuildChoices(List<ServiceOption> services) {
        servicesGroup.selectToggle(null);
        servicesGroup.getToggles().clear();
        serviceOptions.getChildren().clear();
        for (ServiceOption service : services) {
            RadioButton choice = new RadioButton(service.name());
            choice.setUserData(service);
            choice.setToggleGroup(servicesGroup);
            choice.setWrapText(true);
            choice.setMinHeight(72);
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.getStyleClass().add("service-choice");
            // El teclado mueve el foco entre opciones: la lista se desplaza para mostrarlo.
            choice.focusedProperty().addListener((observable, was, focused) -> {
                if (focused) {
                    scrollToVisible(choice);
                }
            });
            serviceOptions.getChildren().add(choice);
        }
        serviceScroll.setVvalue(0);
    }

    // Ajusta solo el desplazamiento de la lista de servicios para que la opción quede completa.
    private void scrollToVisible(Node choice) {
        serviceScroll.applyCss();
        serviceScroll.layout();
        double content = serviceOptions.getLayoutBounds().getHeight();
        double view = serviceScroll.getViewportBounds().getHeight();
        double scrollable = content - view;
        if (scrollable <= 0) {
            return;
        }
        Bounds bounds = choice.getBoundsInParent();
        double top = serviceScroll.getVvalue() * scrollable;
        if (bounds.getMinY() < top) {
            top = bounds.getMinY();
        } else if (bounds.getMaxY() > top + view) {
            top = bounds.getMaxY() - view;
        }
        serviceScroll.setVvalue(Math.max(0, Math.min(1, top / scrollable)));
    }

    private void restoreFocus(Focus focus) {
        if (confirmationShowing || closed) {
            return;
        }
        if (focus == Focus.REFRESH) {
            refreshButton.requestFocus();
        } else if (focus == Focus.LIST) {
            focusSelectedOrFirstChoice();
        }
    }

    private void focusSelectedOrFirstChoice() {
        Toggle selected = servicesGroup.getSelectedToggle();
        if (selected instanceof Node node) {
            node.requestFocus();
        } else if (!serviceOptions.getChildren().isEmpty()) {
            serviceOptions.getChildren().get(0).requestFocus();
        }
    }

    // Envía una sola solicitud para la selección actual.
    // Si se pierde la confirmación, informa la incertidumbre sin repetir la emisión.
    @FXML
    private void generateTicket(ActionEvent event) {
        if (busy || closed || confirmationShowing || ticketRequest == null) {
            return;
        }
        if (!catalogLoaded || servicesGroup.getToggles().isEmpty()) {
            requestCatalog(null, Focus.NONE);
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
            Throwable error = task.getException();
            showFailure("No se recibió la confirmación del turno. Consulte al personal para comprobar "
                    + "si fue generado antes de volver a solicitarlo.", error);
            // Un rechazo explícito del servidor (por ejemplo, un servicio dado de baja) demuestra que no
            // se emitió ningún turno: es un punto seguro para actualizar la lista obsoleta. Una
            // confirmación perdida no lo es y conserva su aviso sin consultar nada.
            if ((error instanceof IllegalArgumentException || error instanceof IllegalStateException)
                    && error.getMessage() != null) {
                requestCatalog(error.getMessage(), Focus.NONE);
            }
        });
        startTask(task);
    }

    private ServiceOption selectedService() {
        Toggle selected = servicesGroup.getSelectedToggle();
        return selected == null ? null : (ServiceOption) selected.getUserData();
    }

    private void updateSelection() {
        ServiceOption selected = selectedService();
        String name = selected == null ? "Elija un servicio" : selected.name();
        selectedServiceLabel.setText(name);
        // Los nombres largos reducen la letra para que el panel y la prioridad sigan visibles.
        int size = name.length() <= 30 ? 27 : name.length() <= 55 ? 21 : 17;
        selectedServiceLabel.setStyle("-fx-font-size: " + size + "px;");
        attentionLabel.setText(priorityCheck.isSelected() ? "Atención preferencial" : "Atención regular");
    }

    // Presenta los datos confirmados y mantiene la emisión bloqueada hasta preparar otra
    // persona.
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

    // Limpia la selección y vuelve al formulario sin emitir un turno adicional. Es el punto
    // seguro entre personas: actualiza el catálogo para reflejar altas, cambios y bajas.
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
        if (servicesRequest != null) {
            requestCatalog(null, Focus.LIST);
        }
    }

    // Task ejecuta la red en un hilo virtual y entrega sus eventos al hilo de JavaFX.
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
        refreshButton.setDisable(closed || busy || confirmationShowing || servicesRequest == null);
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

    // Cancela la tarea pendiente y evita aplicar sus resultados a la pantalla cerrada.
    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        setBusy(false);
    }
}
