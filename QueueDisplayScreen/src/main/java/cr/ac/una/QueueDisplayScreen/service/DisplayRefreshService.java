package cr.ac.una.QueueDisplayScreen.service;

import cr.ac.una.QueueDisplayScreen.model.DisplaySnapshot;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DisplayRefreshService implements AutoCloseable {

    @FunctionalInterface
    public interface SnapshotRequest {

        DisplaySnapshot get() throws IOException;
    }

    private final SnapshotRequest request;
    private final Consumer<DisplaySnapshot> onSuccess;
    private final Consumer<Exception> onFailure;
    private final ScheduledExecutorService scheduler;
    private boolean started;
    private volatile boolean closed;

    public DisplayRefreshService(
            SnapshotRequest request,
            Consumer<DisplaySnapshot> onSuccess,
            Consumer<Exception> onFailure
    ) {
        this.request = Objects.requireNonNull(request, "La consulta es obligatoria.");
        this.onSuccess = Objects.requireNonNull(onSuccess, "La acción de éxito es obligatoria.");
        this.onFailure = Objects.requireNonNull(onFailure, "La acción de error es obligatoria.");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("queuedisplay-refresh").factory()
        );
    }

    public synchronized void start() {
        if (closed || started) {
            throw new IllegalStateException("La actualización no se puede iniciar nuevamente.");
        }
        started = true;
        scheduler.scheduleWithFixedDelay(this::refresh, 0, 2, TimeUnit.SECONDS);
    }

    private void refresh() {
        if (closed) {
            return;
        }

        DisplaySnapshot snapshot;
        try {
            snapshot = Objects.requireNonNull(
                    request.get(), "El servidor no devolvió una captura."
            );
        } catch (IOException | RuntimeException exception) {
            if (!closed) {
                onFailure.accept(exception);
            }
            return;
        }

        if (!closed) {
            onSuccess.accept(snapshot);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        scheduler.shutdownNow();
    }
}
