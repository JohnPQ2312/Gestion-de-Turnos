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

    private AssignmentsRequest assignmentsRequest;
    private CallNextRequest callNextRequest;
    private ActiveTicketRequest activeTicketRequest;
    private Task<?> currentTask;
    private boolean assignmentsLoaded;
    private boolean activeTicketKnown;
    private boolean hasActiveTicket;
    private boolean busy;
    private boolean closed;

    @FXML
    private ComboBox<OperatorAssignment> assignmentCombo;
    @FXML
    private Button callNextButton;
    @FXML
    private Button reloadAssignmentsButton;
    @FXML
    private Label activeTicketLabel;

    public void configure(
            AssignmentsRequest assignmentsRequest,
            CallNextRequest callNextRequest,
            ActiveTicketRequest activeTicketRequest
    ) {
        Objects.requireNonNull(assignmentsRequest, "La consulta de asignaciones es obligatoria.");
        Objects.requireNonNull(callNextRequest, "La solicitud de llamado es obligatoria.");
        Objects.requireNonNull(activeTicketRequest, "La consulta del turno activo es obligatoria.");

        if (busy || closed) {
            throw new IllegalStateException("La pantalla no se puede configurar en este momento.");
        }

        this.assignmentsRequest = assignmentsRequest;
        this.callNextRequest = callNextRequest;
        this.activeTicketRequest = activeTicketRequest;
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
            hasActiveTicket = false;
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
        hasActiveTicket = false;
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

    private void loadActiveTicket(Throwable failedCall) {
        if (busy || closed || !assignmentsLoaded || activeTicketRequest == null) {
            return;
        }

        OperatorAssignment selected = assignmentCombo.getValue();
        if (selected == null) {
            return;
        }

        activeTicketKnown = false;
        hasActiveTicket = false;
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
            hasActiveTicket = active.isPresent();

            if (active.isPresent()) {
                ActiveTicket ticket = active.get();
                showTicket(ticket.visualCode(), ticket.serviceName(),
                        ticket.operatorId(), ticket.windowId(), ticket.state());
            } else {
                activeTicketLabel.setText("Sin turno activo en esta asignación.");
            }

            setBusy(false);

            if (failedCall != null && (active.isEmpty()
                    || failedCall instanceof IllegalArgumentException
                    || failedCall instanceof IllegalStateException)) {
                showFailure(
                        "No se recibió la confirmación del llamado. "
                        + "La consulta actual no muestra un turno activo; "
                        + "compruebe el estado antes de repetir la solicitud.",
                        failedCall
                );
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
            hasActiveTicket = false;
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

    @FXML
    private void callNext() {
        if (busy || closed || !assignmentsLoaded || !activeTicketKnown
                || hasActiveTicket || callNextRequest == null) {
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

            callNextButton.setText("Llamar siguiente");
            if (!selected.equals(assignmentCombo.getValue())) {
                setBusy(false);
                loadActiveTicket(null);
                return;
            }

            Optional<CalledTicket> next = task.getValue();
            activeTicketKnown = true;
            hasActiveTicket = next.isPresent();

            if (next.isPresent()) {
                CalledTicket ticket = next.get();
                showTicket(ticket.visualCode(), ticket.serviceName(),
                        ticket.operatorId(), ticket.windowId(), "LLAMADO");
            } else {
                activeTicketLabel.setText("Sin turno activo en esta asignación.");
            }

            setBusy(false);
            if (next.isEmpty()) {
                showMessage(Alert.AlertType.INFORMATION, "No hay turnos compatibles en espera.");
            }
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }

            callNextButton.setText("Llamar siguiente");
            activeTicketKnown = false;
            setBusy(false);
            System.err.println("No se recibió la confirmación del llamado. Detalle: "
                    + task.getException());

            loadActiveTicket(selected.equals(assignmentCombo.getValue())
                    ? task.getException() : null);
        });

        startTask(task);
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
        assignmentCombo.setDisable(closed || busy || !assignmentsLoaded || noAssignments);
        callNextButton.setDisable(closed || busy || !assignmentsLoaded
                || !activeTicketKnown || hasActiveTicket
                || assignmentCombo.getValue() == null || callNextRequest == null);
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
