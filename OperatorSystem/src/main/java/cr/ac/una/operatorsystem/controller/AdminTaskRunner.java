package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.AccessDeniedException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

// Ejecuta solicitudes administrativas fuera del hilo JavaFX con el mismo criterio de Personas:
// bloquea los controles, distingue DENIED, errores de negocio y fallas de red, y nunca reenvía una escritura.
final class AdminTaskRunner {
    private final Node content;
    private final Button reloadButton;
    private final Label noticeLabel;
    private final String threadName;
    private Consumer<String> sessionEnded = message -> { };
    private Task<?> currentTask;
    private boolean busy;
    private boolean closed;
    private boolean refreshRequired;

    AdminTaskRunner(Node content, Button reloadButton, Label noticeLabel, String threadName) {
        this.content = Objects.requireNonNull(content);
        this.reloadButton = Objects.requireNonNull(reloadButton);
        this.noticeLabel = Objects.requireNonNull(noticeLabel);
        this.threadName = threadName;
    }

    void onSessionEnded(Consumer<String> handler) { sessionEnded = Objects.requireNonNull(handler); }

    boolean isIdle() { return !busy && !closed; }

    <T> void run(Callable<T> action, Consumer<T> success, boolean write) {
        if (busy || closed) return;
        busy = true;
        content.setDisable(true);
        reloadButton.setDisable(true);
        noticeLabel.setText(write ? "Guardando…" : "Consultando…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        currentTask = task;
        task.setOnSucceeded(event -> {
            if (closed) return;
            if (!write) refreshRequired = false;
            release();
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (closed) return;
            release();
            Throwable error = task.getException();
            if (error instanceof AccessDeniedException denied) {
                noticeLabel.setText(denied.getMessage());
                if (denied.endsSession()) sessionEnded.accept(denied.getMessage());
            } else if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
                noticeLabel.setText(error.getMessage());
            } else if (write) {
                // Una confirmación perdida no demuestra que el cambio falló: se exige consultar antes de seguir.
                refreshRequired = true;
                content.setDisable(true);
                noticeLabel.setText("No se recibió una confirmación válida. El cambio pudo guardarse: presione Actualizar "
                        + "y revise el registro antes de repetirlo. No se reenvió la operación.");
            } else {
                noticeLabel.setText("No se pudieron consultar los datos; las filas visibles pueden estar desactualizadas. "
                        + "Compruebe CoreServer y presione Actualizar.");
            }
        });
        Thread.ofVirtual().name(threadName).start(task);
    }

    private void release() {
        busy = false;
        currentTask = null;
        content.setDisable(refreshRequired);
        reloadButton.setDisable(false);
    }

    void close() {
        closed = true;
        if (currentTask != null) currentTask.cancel(true);
    }
}
