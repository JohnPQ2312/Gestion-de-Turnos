package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.model.ActiveTicket;
import cr.ac.una.operatorsystem.model.CalledTicket;
import cr.ac.una.operatorsystem.model.OperatorAssignment;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private enum AttentionOperation {
        CALL, START, FINISH
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
            activeTicketKnown = false;
            currentTicket = null;
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
        activeTicketLabel.setText("Consultando turno activo...");

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

            if (currentTicket != null) {
                showTicket(currentTicket.visualCode(), currentTicket.serviceName(),
                        currentTicket.operatorId(), currentTicket.windowId(), currentTicket.state());
            } else {
                activeTicketLabel.setText("Sin turno activo en esta asignación.");
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
            case FINISH ->
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
                activeTicketLabel.setText("Sin turno activo en esta asignación.");
                setBusy(false);
                showMessage(Alert.AlertType.INFORMATION, "No hay turnos compatibles en espera.");
            }
        });

        task.setOnFailed(event -> {
            if (closed) {
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
        changeAttention(AttentionOperation.FINISH);
    }

    private void changeAttention(AttentionOperation operation) {
        boolean starting = operation == AttentionOperation.START;
        AttentionRequest request = starting ? startAttentionRequest : finishAttentionRequest;
        String expectedState = starting ? "LLAMADO" : "EN_ATENCION";

        if (busy || closed || !assignmentsLoaded || !activeTicketKnown
                || currentTicket == null || request == null
                || !expectedState.equals(currentTicket.state())) {
            return;
        }

        OperatorAssignment selected = assignmentCombo.getValue();
        if (selected == null) {
            return;
        }

        ActiveTicket target = currentTicket;
        activeTicketKnown = false;
        Button operationButton = starting ? startAttentionButton : finishAttentionButton;
        operationButton.setText(starting ? "Iniciando..." : "Finalizando...");

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

            resetOperationButtons();
            setBusy(false);
            loadActiveTicket(null);
        });

        task.setOnFailed(event -> {
            if (closed) {
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
    }

    private void showTicket(
            String visualCode, String serviceName, int operatorId, int windowId, String state
    ) {
        String stateName = "LLAMADO".equals(state) ? "Llamado" : "En atención";
        activeTicketLabel.setText(
                "Turno: " + visualCode
                + "\nServicio: " + serviceName
                + "\nOperador: " + operatorId + " - Ventanilla: " + windowId
                + "\nEstado: " + stateName
        );
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
        callNextButton.setDisable(actionsBlocked || currentTicket != null || callNextRequest == null);
        startAttentionButton.setDisable(actionsBlocked || startAttentionRequest == null
                || currentTicket == null || !"LLAMADO".equals(currentTicket.state()));
        finishAttentionButton.setDisable(actionsBlocked || finishAttentionRequest == null
                || currentTicket == null || !"EN_ATENCION".equals(currentTicket.state()));
        reloadAssignmentsButton.setDisable(closed || busy || assignmentsRequest == null);
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

    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        updateControls();
    }
}
