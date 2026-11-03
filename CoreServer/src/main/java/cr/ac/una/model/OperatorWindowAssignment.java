package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class OperatorWindowAssignment {

    private final long assignmentId;
    private final int operatorId;
    private final int windowId;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;

    public OperatorWindowAssignment(
            long assignmentId,
            int operatorId,
            int windowId,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        if (assignmentId <= 0 || operatorId <= 0 || windowId <= 0) {
            throw new IllegalArgumentException(
                    "Los identificadores de la asignación, "
                    + "el operador y la ventanilla deben ser positivos."
            );
        }

        this.startedAt = Objects.requireNonNull(
                startedAt, "La fecha de inicio es obligatoria."
        );

        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "La asignación no puede terminar antes de su inicio."
            );
        }

        this.assignmentId = assignmentId;
        this.operatorId = operatorId;
        this.windowId = windowId;
        this.endedAt = endedAt;
    }

    public long getAssignmentId() {
        return assignmentId;
    }

    public int getOperatorId() {
        return operatorId;
    }

    public int getWindowId() {
        return windowId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public boolean isOpen() {
        return endedAt == null;
    }
}
