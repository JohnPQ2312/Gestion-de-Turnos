package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.AccessDeniedException;
import cr.ac.una.operatorsystem.model.ActiveTicket;
import cr.ac.una.operatorsystem.model.CalledTicket;
import cr.ac.una.operatorsystem.model.OperatorAssignment;
import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import javafx.css.PseudoClass;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

public class OperatorController {

    @FunctionalInterface
    public interface AssignmentsRequest {

        List<OperatorAssignment> list() throws IOException;
    }

    @FunctionalInterface
    public interface CallNextRequest {

        Optional<CalledTicket> callNext(long assignmentId) throws IOException;
    }

    @FunctionalInterface
    public interface ActiveTicketRequest {

        Optional<ActiveTicket> get(long assignmentId) throws IOException;
    }

    @FunctionalInterface
    public interface AttentionRequest {

        void execute(long assignmentId, long ticketId) throws IOException;
    }

    /** Consulta o cambia el estado de la ventanilla; devuelve el estado resultante. */
    @FunctionalInterface
    public interface WindowStateRequest {

        String apply(long assignmentId) throws IOException;
    }

    private enum AttentionOperation {
        CALL, START, FINISH, RECALL
    }

    private record FailedOperation(
            AttentionOperation operation, Long ticketId, Throwable cause
            ) {

    }

    private AssignmentsRequest assignmentsRequest;
    private CallNextRequest callNextRequest;
    private ActiveTicketRequest activeTicketRequest;
    private AttentionRequest startAttentionRequest;
    private AttentionRequest finishAttentionRequest;
    private AttentionRequest recallRequest;
    private ActiveTicket currentTicket;
    private Task<?> currentTask;
    private boolean assignmentsLoaded;
    private boolean activeTicketKnown;
    private boolean busy;
    private boolean closed;

    @FXML
    private ComboBox<OperatorAssignment> assignmentCombo;
    @FXML
    private Button callNextButton;
    @FXML
    private Button reloadAssignmentsButton;
    @FXML
    private Button startAttentionButton;
    @FXML
    private Button finishAttentionButton;
    @FXML
    private Label activeTicketLabel;

    @FXML private BorderPane workspace;
    @FXML private HBox finishOverlay;
    @FXML private Label ticketCodeLabel;
    @FXML private Label serviceNameLabel;
    @FXML private Label ticketStateLabel;
    @FXML private Label calledStepLabel;
    @FXML private Label attendingStepLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private Label lastFinishedLabel;
    @FXML private Label finishCodeLabel;
    @FXML private Label finishServiceLabel;
    @FXML private Button reportsButton;
    @FXML private Button peopleButton;
    private Runnable peopleOpener;
    @FXML private Button assignmentsButton;
    private Runnable assignmentsOpener;
    @FXML private Button catalogButton;
    private Runnable catalogOpener;
    @FXML private Button logoutButton;
    @FXML private Label sessionUserLabel;
    @FXML private Button pauseButton;
    @FXML private Button recallButton;
    @FXML private Label recallNoticeLabel;
    @FXML private Label windowStateLabel;

    private WindowStateRequest windowStateRequest;
    private WindowStateRequest pauseRequest;
    private WindowStateRequest resumeRequest;
    // Estado de la ventanilla según el servidor; null mientras no se conoce.
    private String windowState;
    private boolean pauseInProgress;

    private Runnable reportsOpener;
    private Runnable logoutHandler;
    private Consumer<String> sessionEndedHandler;
    private String displayedCode = "";
    private String displayedService = "";
    private String displayedState = "";
    private boolean finishConfirmationOpen;
    private final Map<Long, String> lastFinishedByAssignment = new HashMap<>();

    public void configure(
            AssignmentsRequest assignmentsRequest,
            CallNextRequest callNextRequest,
            ActiveTicketRequest activeTicketRequest,
            AttentionRequest startAttentionRequest,
            AttentionRequest finishAttentionRequest
    ) {
        Objects.requireNonNull(assignmentsRequest, "La consulta de asignaciones es obligatoria.");
        Objects.requireNonNull(callNextRequest, "La solicitud de llamado es obligatoria.");
        Objects.requireNonNull(activeTicketRequest, "La consulta del turno activo es obligatoria.");
        Objects.requireNonNull(startAttentionRequest, "La solicitud de inicio es obligatoria.");
        Objects.requireNonNull(finishAttentionRequest, "La solicitud de finalización es obligatoria.");

        if (busy || closed) {
            throw new IllegalStateException("La pantalla no se puede configurar en este momento.");
        }

        this.assignmentsRequest = assignmentsRequest;
        this.callNextRequest = callNextRequest;
        this.activeTicketRequest = activeTicketRequest;
        this.startAttentionRequest = startAttentionRequest;
        this.finishAttentionRequest = finishAttentionRequest;
        reloadAssignments();
    }

    @FXML
    private void initialize() {
        assignmentCombo.setConverter(new StringConverter<OperatorAssignment>() {
            @Override
            public String toString(OperatorAssignment assignment) {
                return assignment == null ? ""
                        : "Operador " + assignment.operatorId()
                        + " - Ventanilla " + assignment.windowId();
            }

            @Override
            public OperatorAssignment fromString(String text) {
                throw new UnsupportedOperationException(
                        "La asignación se selecciona desde la lista."
                );
            }
        });

        assignmentCombo.valueProperty().addListener((observable, previous, selected) -> {
            recallNoticeLabel.setVisible(false);
            recallNoticeLabel.setManaged(false);
            activeTicketKnown = false;
            currentTicket = null;
            windowState = null;
            clearTicketPresentation();
            activeTicketLabel.setText("Seleccione una asignación para consultar su turno.");
            updateControls();

            if (!busy && assignmentsLoaded && selected != null) {
                loadActiveTicket(null);
            }
        });
        updateControls();
    }

    @FXML
    private void reloadAssignments() {
        if (busy || closed || assignmentsRequest == null) {
            return;
        }

        OperatorAssignment previous = assignmentCombo.getValue();
        assignmentsLoaded = false;
        activeTicketKnown = false;
        currentTicket = null;
        clearTicketPresentation();
        assignmentCombo.getItems().clear();
        assignmentCombo.setPromptText("Consultando asignaciones...");
        activeTicketLabel.setText("Seleccione una asignación para consultar su turno.");
        reloadAssignmentsButton.setText("Consultando...");

        Task<List<OperatorAssignment>> task = new Task<>() {
            @Override
            protected List<OperatorAssignment> call() throws IOException {
                return List.copyOf(assignmentsRequest.list());
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }

            assignmentsLoaded = true;
            assignmentCombo.getItems().setAll(task.getValue());
            assignmentCombo.setPromptText(task.getValue().isEmpty()
                    ? "No hay asignaciones activas" : "Seleccione operador y ventanilla");

            if (previous != null) {
                task.getValue().stream()
                        .filter(assignment -> assignment.equals(previous))
                        .findFirst()
                        .ifPresent(assignmentCombo.getSelectionModel()::select);
            }

            reloadAssignmentsButton.setText("Actualizar asignaciones");
            setBusy(false);

            if (assignmentCombo.getValue() != null) {
                loadActiveTicket(null);
            }
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            if (handleAccessDenied(task.getException())) {
                return;
            }

            assignmentCombo.setPromptText("No se pudieron cargar las asignaciones");
            reloadAssignmentsButton.setText("Reintentar conexión");
            setBusy(false);
            showFailure(
                    "No se pudieron consultar las asignaciones. "
                    + "Compruebe CoreServer y su conexión a MySQL.",
                    task.getException()
            );
        });

        startTask(task);
    }

    private void loadActiveTicket(FailedOperation failedOperation) {
        if (busy || closed || !assignmentsLoaded || activeTicketRequest == null) {
            return;
        }

        OperatorAssignment selected = assignmentCombo.getValue();
        if (selected == null) {
            return;
        }

        activeTicketKnown = false;
        currentTicket = null;
        windowState = null;
        activeTicketLabel.setText("Consultando turno activo...");

        // El estado de la ventanilla se consulta en la misma tarea, después
        // del turno activo, para que pantalla y botones partan del servidor.
        WindowStateRequest stateRequest = windowStateRequest;
        AtomicReference<String> fetchedState = new AtomicReference<>();

        Task<Optional<ActiveTicket>> task = new Task<>() {
            @Override
            protected Optional<ActiveTicket> call() throws IOException {
                Optional<ActiveTicket> active = Objects.requireNonNull(
                        activeTicketRequest.get(selected.assignmentId()),
                        "La respuesta del turno activo es obligatoria."
                );

                if (active.isPresent()) {
                    ActiveTicket ticket = active.get();
                    if (ticket.assignmentId() != selected.assignmentId()
                            || ticket.operatorId() != selected.operatorId()
                            || ticket.windowId() != selected.windowId()) {
                        throw new IOException(
                                "El turno activo no corresponde a la asignación seleccionada."
                        );
                    }
                }

                if (stateRequest != null) {
                    fetchedState.set(stateRequest.apply(selected.assignmentId()));
                }

                return active;
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }

            if (!selected.equals(assignmentCombo.getValue())) {
                setBusy(false);
                loadActiveTicket(null);
                return;
            }

            Optional<ActiveTicket> active = task.getValue();
            activeTicketKnown = true;
            currentTicket = active.orElse(null);
            windowState = fetchedState.get();

            if (currentTicket != null) {
                showTicket(currentTicket.visualCode(), currentTicket.serviceName(),
                        currentTicket.operatorId(), currentTicket.windowId(), currentTicket.state());
            } else {
                clearTicketPresentation();
                activeTicketLabel.setText("PAUSADA".equals(windowState)
                        ? "Ventanilla en pausa. Reanúdela para llamar turnos."
                        : "Puede llamar al siguiente turno compatible.");
            }

            setBusy(false);

            if (failedOperation != null && !recoveredAfterFailure(failedOperation, active)) {
                showFailure(failureMessage(failedOperation.operation()), failedOperation.cause());
            }
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            if (handleAccessDenied(task.getException())) {
                return;
            }

            if (!selected.equals(assignmentCombo.getValue())) {
                setBusy(false);
                loadActiveTicket(null);
                return;
            }

            activeTicketKnown = false;
            currentTicket = null;
            activeTicketLabel.setText("Estado sin confirmar. Actualice las asignaciones.");
            setBusy(false);
            showFailure(
                    "No se pudo consultar el turno activo. "
                    + "Actualice las asignaciones para comprobar su estado.",
                    task.getException()
            );
        });

        startTask(task);
    }

    private boolean recoveredAfterFailure(
            FailedOperation failure,
            Optional<ActiveTicket> active
    ) {
        if (!(failure.cause() instanceof IOException)) {
            return false;
        }

        return switch (failure.operation()) {
            case CALL ->
                active.isPresent();
            case START ->
                active.isPresent()
                && Objects.equals(active.get().ticketId(), failure.ticketId())
                && "EN_ATENCION".equals(active.get().state());
            case FINISH, RECALL ->
                false;
        };
    }

    private String failureMessage(AttentionOperation operation) {
        return switch (operation) {
            case CALL ->
                "No se recibió la confirmación del llamado. "
                + "Compruebe el estado actual antes de repetir la solicitud.";
            case START ->
                "No se recibió la confirmación del inicio de atención. "
                + "Compruebe el estado actual antes de repetir la solicitud.";
            case RECALL ->
                "No se recibió la confirmación del rellamado. "
                + "Compruebe la pantalla pública antes de volver a solicitarlo.";
            case FINISH ->
                "No se recibió la confirmación de finalización. "
                + "Compruebe el estado y el historial del turno antes de repetir la solicitud.";
        };
    }

    @FXML
    private void callNext() {
        if (busy || closed || !assignmentsLoaded || !activeTicketKnown
                || currentTicket != null || callNextRequest == null) {
            return;
        }

        OperatorAssignment selected = assignmentCombo.getValue();
        if (selected == null) {
            return;
        }

        activeTicketKnown = false;
        callNextButton.setText("Llamando...");

        Task<Optional<CalledTicket>> task = new Task<>() {
            @Override
            protected Optional<CalledTicket> call() throws IOException {
                Optional<CalledTicket> next = Objects.requireNonNull(
                        callNextRequest.callNext(selected.assignmentId()),
                        "La respuesta de llamado es obligatoria."
                );

                if (next.isPresent()) {
                    CalledTicket ticket = next.get();
                    if (ticket.operatorId() != selected.operatorId()
                            || ticket.windowId() != selected.windowId()) {
                        throw new IOException(
                                "La confirmación no corresponde al operador y ventanilla seleccionados."
                        );
                    }
                }

                return next;
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }

            resetOperationButtons();
            if (!selected.equals(assignmentCombo.getValue())) {
                setBusy(false);
                loadActiveTicket(null);
                return;
            }

            Optional<CalledTicket> next = task.getValue();
            if (next.isPresent()) {
                CalledTicket ticket = next.get();
                showTicket(ticket.visualCode(), ticket.serviceName(),
                        ticket.operatorId(), ticket.windowId(), "LLAMADO");
                setBusy(false);
                loadActiveTicket(null);
            } else {
                activeTicketKnown = true;
                currentTicket = null;
                clearTicketPresentation();
                activeTicketLabel.setText("Puede llamar al siguiente turno compatible.");
                setBusy(false);
                showMessage(Alert.AlertType.INFORMATION, "No hay turnos compatibles en espera.");
            }
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            if (handleAccessDenied(task.getException())) {
                return;
            }

            resetOperationButtons();
            activeTicketKnown = false;
            setBusy(false);
            FailedOperation failure = new FailedOperation(
                    AttentionOperation.CALL, null, task.getException()
            );
            logFailure(failure);
            loadActiveTicket(selected.equals(assignmentCombo.getValue()) ? failure : null);
        });

        startTask(task);
    }

    @FXML
    private void startAttention() {
        changeAttention(AttentionOperation.START);
    }

    @FXML
    private void finishAttention() {
        if (busy || closed || !activeTicketKnown || currentTicket == null
                || !"EN_ATENCION".equals(currentTicket.state())
                || finishAttentionRequest == null) {
            return;
        }
        finishCodeLabel.setText(currentTicket.visualCode());
        finishCodeLabel.setStyle("-fx-font-size: "
                + (currentTicket.visualCode().length() > 10 ? 26 : 48) + "px;");
        finishServiceLabel.setText(currentTicket.serviceName());
        finishConfirmationOpen = true;
        updatePresentation();
    }

    @FXML
    private void cancelFinishConfirmation() {
        finishConfirmationOpen = false;
        updatePresentation();
        finishAttentionButton.requestFocus();
    }

    @FXML
    private void confirmFinishAttention() {
        if (!finishConfirmationOpen) {
            return;
        }
        finishConfirmationOpen = false;
        updatePresentation();
        changeAttention(AttentionOperation.FINISH);
    }

    public void configureRecall(AttentionRequest recallRequest) {
        this.recallRequest = Objects.requireNonNull(recallRequest, "La solicitud de rellamado es obligatoria.");
        updateControls();
    }

    @FXML
    private void recallTicket() {
        changeAttention(AttentionOperation.RECALL);
    }

    private void changeAttention(AttentionOperation operation) {
        boolean starting = operation == AttentionOperation.START;
        boolean recalling = operation == AttentionOperation.RECALL;
        AttentionRequest request = recalling ? recallRequest
                : starting ? startAttentionRequest : finishAttentionRequest;
        String expectedState = starting || recalling ? "LLAMADO" : "EN_ATENCION";

        if (busy || closed || !assignmentsLoaded || !activeTicketKnown
                || currentTicket == null || request == null
                || !expectedState.equals(currentTicket.state())
                || (recalling && !"ACTIVA".equals(windowState))) {
            return;
        }

        OperatorAssignment selected = assignmentCombo.getValue();
        if (selected == null) {
            return;
        }

        ActiveTicket target = currentTicket;
        activeTicketKnown = false;
        recallNoticeLabel.setVisible(false);
        recallNoticeLabel.setManaged(false);
        Button operationButton = recalling ? recallButton
                : starting ? startAttentionButton : finishAttentionButton;
        operationButton.setText(recalling ? "Volviendo a llamar..."
                : starting ? "Iniciando..." : "Finalizando...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                request.execute(selected.assignmentId(), target.ticketId());
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }

            if (recalling) {
                recallNoticeLabel.setText("Se confirmó el rellamado de " + target.visualCode() + ".");
                recallNoticeLabel.setVisible(true);
                recallNoticeLabel.setManaged(true);
            }
            if (!starting && !recalling) {
                lastFinishedByAssignment.put(selected.assignmentId(), target.visualCode());
            }
            resetOperationButtons();
            setBusy(false);
            loadActiveTicket(null);
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            if (handleAccessDenied(task.getException())) {
                return;
            }

            resetOperationButtons();
            activeTicketKnown = false;
            setBusy(false);
            FailedOperation failure = new FailedOperation(
                    operation, target.ticketId(), task.getException()
            );
            logFailure(failure);
            loadActiveTicket(selected.equals(assignmentCombo.getValue()) ? failure : null);
        });

        startTask(task);
    }

    private void logFailure(FailedOperation failure) {
        System.err.println("Operación sin confirmación exitosa: " + failure.operation()
                + ". Detalle: " + failure.cause());
    }

    private void resetOperationButtons() {
        callNextButton.setText("Llamar siguiente");
        startAttentionButton.setText("Iniciar atención");
        finishAttentionButton.setText("Finalizar atención");
        recallButton.setText("Volver a llamar");
    }

    private void showTicket(
            String visualCode, String serviceName, int operatorId, int windowId, String state
    ) {
        displayedCode = visualCode;
        displayedService = serviceName;
        displayedState = state;
        activeTicketLabel.setText("LLAMADO".equals(state)
                ? "Cuando la persona llegue, inicie la atención."
                : "Finalice cuando haya concluido la atención.");
        updatePresentation();
    }

    private void clearTicketPresentation() {
        displayedCode = "";
        displayedService = "";
        displayedState = "";
    }

    // La vista representa respuestas confirmadas; no cambia las reglas del servidor.
    private void updatePresentation() {
        OperatorAssignment selected = assignmentCombo.getValue();
        boolean hasTicket = !displayedCode.isEmpty();
        ticketCodeLabel.setText(hasTicket ? displayedCode : "—");
        int codeSize = !hasTicket ? 54 : displayedCode.length() > 12 ? 28
                : displayedCode.length() > 8 ? 48 : displayedCode.length() > 6 ? 76 : 112;
        ticketCodeLabel.setStyle("-fx-font-size: " + codeSize + "px;");
        serviceNameLabel.setText(hasTicket ? displayedService : selected == null
                ? "Seleccione operador y ventanilla" : "Listo para el siguiente turno");
        String status = !activeTicketKnown ? "Estado sin confirmar"
                : !hasTicket ? "Sin turno activo" : "LLAMADO".equals(displayedState)
                ? "Llamado" : "En atención";
        ticketStateLabel.setText(status);
        ticketStateLabel.setVisible(!activeTicketKnown || !hasTicket);
        ticketStateLabel.setManaged(!activeTicketKnown || !hasTicket);
        ticketStateLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("called"),
                activeTicketKnown && "LLAMADO".equals(displayedState));
        ticketStateLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("attending"),
                activeTicketKnown && "EN_ATENCION".equals(displayedState));
        calledStepLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("current"),
                activeTicketKnown && "LLAMADO".equals(displayedState));
        attendingStepLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("current"),
                activeTicketKnown && "EN_ATENCION".equals(displayedState));
        connectionStatusLabel.setText(closed ? "Ventana cerrada" : busy ? "Consultando…"
                : !assignmentsLoaded ? "Pendiente de consulta" : selected == null
                ? "Seleccione una asignación" : activeTicketKnown
                ? "Estado consultado" : "Estado sin confirmar");
        connectionStatusLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("warning"),
                !busy && !activeTicketKnown);
        Button visibleAction = "LLAMADO".equals(displayedState) ? startAttentionButton
                : "EN_ATENCION".equals(displayedState) ? finishAttentionButton : callNextButton;
        for (Button button : List.of(callNextButton, startAttentionButton, finishAttentionButton)) {
            button.setVisible(button == visibleAction);
            button.setManaged(button == visibleAction);
            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("busy"),
                    busy && button.getText().endsWith("..."));
        }
        String previousCode = selected == null ? null
                : lastFinishedByAssignment.get(selected.assignmentId());
        lastFinishedLabel.setText(previousCode == null
                ? "Sin finalizaciones desde esta ventana."
                : "Última finalizada aquí: " + previousCode);
        finishOverlay.setVisible(finishConfirmationOpen);
        finishOverlay.setManaged(finishConfirmationOpen);
        workspace.setDisable(finishConfirmationOpen);
    }

    private void startTask(Task<?> task) {
        currentTask = task;
        setBusy(true);
        Thread.ofVirtual().name("operatorsystem-request").start(task);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    private void updateControls() {
        boolean noAssignments = assignmentCombo.getItems().isEmpty();
        boolean actionsBlocked = closed || busy || !assignmentsLoaded
                || !activeTicketKnown || assignmentCombo.getValue() == null;

        assignmentCombo.setDisable(closed || busy || !assignmentsLoaded || noAssignments);
        // Con la pausa configurada, solo se llama si el servidor confirmó ACTIVA.
        boolean windowAllowsCall = windowStateRequest == null || "ACTIVA".equals(windowState);
        callNextButton.setDisable(actionsBlocked || currentTicket != null || callNextRequest == null
                || !windowAllowsCall);
        startAttentionButton.setDisable(actionsBlocked || startAttentionRequest == null
                || currentTicket == null || !"LLAMADO".equals(currentTicket.state()));
        finishAttentionButton.setDisable(actionsBlocked || finishAttentionRequest == null
                || currentTicket == null || !"EN_ATENCION".equals(currentTicket.state()));
        recallButton.setDisable(actionsBlocked || recallRequest == null || currentTicket == null
                || !"LLAMADO".equals(currentTicket.state()) || !"ACTIVA".equals(windowState));
        recallButton.setVisible("LLAMADO".equals(displayedState));
        recallButton.setManaged("LLAMADO".equals(displayedState));
        reloadAssignmentsButton.setDisable(closed || busy || assignmentsRequest == null);
        updatePauseControls(actionsBlocked);
        updatePresentation();
    }

    private void showFailure(String message, Throwable error) {
        System.err.println(message + " Detalle: " + error);
        if ((error instanceof IllegalArgumentException
                || error instanceof IllegalStateException)
                && error.getMessage() != null) {
            showMessage(Alert.AlertType.ERROR, error.getMessage());
        } else {
            showMessage(Alert.AlertType.ERROR, message);
        }
    }

    private void showMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(callNextButton.getScene().getWindow());
        alert.setTitle("Atención de turnos");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Habilita el botón Consultas. App indica cómo abrir la vista de reportes;
     * este controlador no conoce el cliente ni la ventana de consultas.
     */
    public void configureAssignments(Runnable opener) {
        assignmentsOpener = Objects.requireNonNull(opener);
        assignmentsButton.setVisible(true);
        assignmentsButton.setManaged(true);
        assignmentsButton.setDisable(closed);
    }

    @FXML
    private void openAssignments() {
        if (!closed && assignmentsOpener != null) assignmentsOpener.run();
    }

    // Servicios y ventanillas: solo ADMIN. App decide cómo abrir la ventana.
    public void configureCatalog(Runnable opener) {
        catalogOpener = Objects.requireNonNull(opener);
        catalogButton.setVisible(true);
        catalogButton.setManaged(true);
        catalogButton.setDisable(closed);
    }

    @FXML
    private void openCatalog() {
        if (!closed && catalogOpener != null) catalogOpener.run();
    }

    public void configurePeople(Runnable opener) {
        peopleOpener = Objects.requireNonNull(opener);
        peopleButton.setVisible(true);
        peopleButton.setManaged(true);
        peopleButton.setDisable(closed);
    }

    @FXML
    private void openPeople() {
        if (!closed && peopleOpener != null) peopleOpener.run();
    }

    public void configureReports(Runnable reportsOpener) {
        this.reportsOpener = Objects.requireNonNull(
                reportsOpener, "La apertura de consultas es obligatoria."
        );
        reportsButton.setDisable(closed);
    }

    @FXML
    private void openReports() {
        if (!closed && reportsOpener != null) {
            reportsOpener.run();
        }
    }

    /** Habilita la pausa y reanudación de la ventanilla (§4.3). */
    public void configurePause(
            WindowStateRequest windowStateRequest,
            WindowStateRequest pauseRequest,
            WindowStateRequest resumeRequest
    ) {
        this.windowStateRequest = Objects.requireNonNull(
                windowStateRequest, "La consulta del estado de la ventanilla es obligatoria."
        );
        this.pauseRequest = Objects.requireNonNull(pauseRequest, "La pausa es obligatoria.");
        this.resumeRequest = Objects.requireNonNull(resumeRequest, "La reanudación es obligatoria.");
        updateControls();
    }

    @FXML
    private void togglePause() {
        if (busy || closed || !assignmentsLoaded || !activeTicketKnown || pauseInProgress) {
            return;
        }

        OperatorAssignment selected = assignmentCombo.getValue();
        boolean pausing = "ACTIVA".equals(windowState);
        boolean resuming = "PAUSADA".equals(windowState);
        WindowStateRequest request = pausing ? pauseRequest : resuming ? resumeRequest : null;
        if (selected == null || request == null) {
            return;
        }

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws IOException {
                return request.apply(selected.assignmentId());
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }
            pauseInProgress = false;
            windowState = task.getValue();
            setBusy(false);
            loadActiveTicket(null);
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            pauseInProgress = false;
            if (handleAccessDenied(task.getException())) {
                return;
            }
            // Sin confirmación no se repite la operación: se consulta el
            // estado guardado en el servidor.
            windowState = null;
            setBusy(false);
            showFailure(pausing
                    ? "No se confirmó la pausa. Se consultará el estado actual de la ventanilla."
                    : "No se confirmó la reanudación. Se consultará el estado actual de la ventanilla.",
                    task.getException());
            loadActiveTicket(null);
        });

        pauseInProgress = true;
        startTask(task);
    }

    private void updatePauseControls(boolean actionsBlocked) {
        boolean paused = "PAUSADA".equals(windowState);

        pauseButton.setDisable(actionsBlocked || pauseInProgress || windowStateRequest == null
                || !("ACTIVA".equals(windowState) || paused));
        pauseButton.setText(pauseInProgress ? (paused ? "Reanudando..." : "Pausando...")
                : paused ? "Reanudar ventanilla" : "Pausar ventanilla");

        windowStateLabel.setText(windowState == null ? ""
                : paused ? "Ventanilla en pausa: no se llaman turnos nuevos."
                : "INACTIVA".equals(windowState) ? "Ventanilla dada de baja."
                : "Ventanilla disponible.");
        windowStateLabel.getStyleClass().removeAll("notice-label", "secondary-text");
        windowStateLabel.getStyleClass().add(paused ? "notice-label" : "secondary-text");
    }

    /**
     * Datos de la sesión iniciada. App decide qué hacer al cerrar la sesión o
     * cuando el servidor la da por terminada.
     */
    public void configureSession(
            String userDescription,
            Runnable logoutHandler,
            Consumer<String> sessionEndedHandler
    ) {
        this.logoutHandler = Objects.requireNonNull(logoutHandler, "El cierre de sesión es obligatorio.");
        this.sessionEndedHandler = Objects.requireNonNull(
                sessionEndedHandler, "El aviso de fin de sesión es obligatorio."
        );
        sessionUserLabel.setText(userDescription == null ? "" : userDescription);
        logoutButton.setDisable(closed);
    }

    /** Oculta Consultas a quien no tiene perfil administrador. */
    public void hideReports() {
        reportsButton.setVisible(false);
        reportsButton.setManaged(false);
    }

    @FXML
    private void logout() {
        if (!closed && logoutHandler != null) {
            logoutHandler.run();
        }
    }

    // Un DENIED del servidor no es una falla de red: la operación no se
    // ejecutó. Si la sesión ya no sirve se vuelve al acceso; si es falta de
    // permiso se informa. En ambos casos el estado del turno queda por
    // confirmar con "Actualizar asignaciones".
    private boolean handleAccessDenied(Throwable error) {
        if (!(error instanceof AccessDeniedException denied)) {
            return false;
        }

        System.err.println("Acceso rechazado por CoreServer: " + denied.getReason());
        resetOperationButtons();
        reloadAssignmentsButton.setText("Actualizar asignaciones");
        activeTicketKnown = false;
        setBusy(false);

        if (denied.endsSession() && sessionEndedHandler != null) {
            sessionEndedHandler.accept(denied.getMessage());
        } else {
            showMessage(Alert.AlertType.ERROR, denied.getMessage());
        }
        return true;
    }

    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        updateControls();
    }
}
