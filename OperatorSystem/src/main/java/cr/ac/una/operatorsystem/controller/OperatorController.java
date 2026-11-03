package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.model.CalledTicket;
import cr.ac.una.operatorsystem.model.OperatorAssignment;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

public class OperatorController {

    @FunctionalInterface
    public interface CallNextRequest {
        Optional<CalledTicket> callNext(long assignmentId);
    }

    private CallNextRequest callNextRequest;

    @FXML
    private ComboBox<OperatorAssignment> assignmentCombo;
    @FXML
    private Button callNextButton;
    @FXML
    private Label lastCalledLabel;

    public void configure(CallNextRequest callNextRequest, List<OperatorAssignment> assignments) {
        Objects.requireNonNull(assignments, "La lista de asignaciones es obligatoria.");
        this.callNextRequest = Objects.requireNonNull(
                callNextRequest, "La solicitud de llamado es obligatoria."
        );
        assignmentCombo.getItems().setAll(assignments);
        assignmentCombo.setPromptText("Asignación de operador y ventanilla");
        assignmentCombo.getSelectionModel().selectFirst();
        assignmentCombo.setDisable(assignments.isEmpty());
        callNextButton.setDisable(assignments.isEmpty());
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
    }

    @FXML
    private void callNext() {
        if (callNextRequest == null) {
            return;
        }
        OperatorAssignment selected = assignmentCombo.getValue();
        if (selected == null) {
            showMessage(Alert.AlertType.WARNING, "Seleccione una asignación.");
            return;
        }
        Optional<CalledTicket> next;
        try {
            next = callNextRequest.callNext(selected.assignmentId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showMessage(Alert.AlertType.ERROR, exception.getMessage());
            return;
        }
        if (next.isEmpty()) {
            showMessage(Alert.AlertType.INFORMATION, "No hay turnos compatibles en espera.");
            return;
        }
        CalledTicket ticket = next.get();
        lastCalledLabel.setText(
                "Turno: " + ticket.visualCode()
                + "\nServicio: " + ticket.serviceName()
                + "\nOperador: " + ticket.operatorId()
                + " - Ventanilla: " + ticket.windowId()
        );
    }

    private void showMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(callNextButton.getScene().getWindow());
        alert.setTitle("Atención de turnos");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
