package cr.ac.una.model;

import java.util.Objects;

// Describe la modalidad de atención y el estado operativo de una ventanilla.
// Una pausa conserva su asignación y permite completar el turno que ya está activo.
public class ServiceWindow {

    private final int windowId;
    private final WindowState state;
    private final AttentionMode attentionMode;

    public ServiceWindow(
            int windowId,
            WindowState state,
            AttentionMode attentionMode
    ) {
        if (windowId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la ventanilla debe ser positivo."
            );
        }

        this.windowId = windowId;
        this.state = Objects.requireNonNull(
                state, "El estado de la ventanilla es obligatorio."
        );
        this.attentionMode = Objects.requireNonNull(
                attentionMode, "La modalidad de atención es obligatoria."
        );
    }

    public int getWindowId() {
        return windowId;
    }

    public WindowState getState() {
        return state;
    }

    public AttentionMode getAttentionMode() {
        return attentionMode;
    }

    // Solo ACTIVA permite pedir un turno nuevo.
    public boolean isActive() {
        return state == WindowState.ACTIVA;
    }

    public boolean isPaused() {
        return state == WindowState.PAUSADA;
    }

    // Incluye la pausa para mantener accesible el turno que ya estaba asignado.
    public boolean isOperational() {
        return state == WindowState.ACTIVA || state == WindowState.PAUSADA;
    }
}