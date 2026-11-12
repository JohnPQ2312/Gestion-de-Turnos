package cr.ac.una.model;

import java.util.Objects;

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

    /** Disponible para llamar turnos. */
    public boolean isActive() {
        return state == WindowState.ACTIVA;
    }

    public boolean isPaused() {
        return state == WindowState.PAUSADA;
    }

    /** En servicio: activa o en pausa temporal (no dada de baja). */
    public boolean isOperational() {
        return state == WindowState.ACTIVA || state == WindowState.PAUSADA;
    }
}