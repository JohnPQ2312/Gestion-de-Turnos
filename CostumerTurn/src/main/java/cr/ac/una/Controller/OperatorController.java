package cr.ac.una.Controller;

import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.Ticket;
import cr.ac.una.service.TicketAttentionService;
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

    private TicketAttentionService attentionService;
    private Runnable onTicketCalled = () -> {
    };

    @FXML
    private ComboBox<OperatorWindowAssignment> assignmentCombo;

    @FXML
    private Button callNextButton;

    @FXML
    private Label lastCalledLabel;

    public void configure(
            TicketAttentionService attentionService,
            List<OperatorWindowAssignment> assignments,
            Runnable onTicketCalled
    ) {
        this.attentionService = Objects.requireNonNull(
                attentionService, "El servicio de atención es obligatorio."
        );
        this.onTicketCalled = Objects.requireNonNull(
                onTicketCalled, "La acción de actualización es obligatoria."
        );

        Objects.requireNonNull(
                assignments, "La lista de asignaciones es obligatoria."
        );

        assignmentCombo.getItems().setAll(
                assignments.stream()
                        .filter(OperatorWindowAssignment::isOpen)
                        .toList()
        );

        assignmentCombo.getSelectionModel().selectFirst();
        callNextButton.setDisable(assignmentCombo.getItems().isEmpty());
    }

    @FXML
    private void initialize() {
        assignmentCombo.setConverter(
                new StringConverter<OperatorWindowAssignment>() {
            @Override
            public String toString(OperatorWindowAssignment assignment) {
                return assignment == null ? ""
                        : "Operador " + assignment.getOperatorId()
                        + " - Ventanilla " + assignment.getWindowId();
            }

            @Override
            public OperatorWindowAssignment fromString(String text) {
                throw new UnsupportedOperationException(
                        "La asignación se selecciona desde la lista."
                );
            }
        }
        );
    }

    @FXML
    private void callNext() {
        OperatorWindowAssignment selected = assignmentCombo.getValue();

        if (selected == null) {
            showMessage(
                    Alert.AlertType.WARNING,
                    "Seleccione una asignación."
            );
            return;
        }

        Optional<Ticket> next;

        try {
            next = attentionService.callNext(selected.getAssignmentId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showMessage(Alert.AlertType.ERROR, exception.getMessage());
            return;
        }

        if (next.isEmpty()) {
            showMessage(
                    Alert.AlertType.INFORMATION,
                    "No hay turnos compatibles en espera."
            );
            return;
        }

        Ticket ticket = next.get();

        lastCalledLabel.setText(
                "Turno: " + ticket.getVisualCode()
                + "\nServicio: " + ticket.getService().getName()
                + "\nOperador: " + ticket.getOperatorId()
                + " - Ventanilla: " + ticket.getWindowId()
        );

        onTicketCalled.run();
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
