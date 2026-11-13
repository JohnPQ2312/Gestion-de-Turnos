package cr.ac.una.QueueDisplayScreen.service;

import cr.ac.una.QueueDisplayScreen.model.DisplaySnapshot;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// Escucha avisos de CoreServer y consulta una captura al conectar y después de cada cambio.
// Si pierde el canal, informa el fallo y vuelve a conectarse mientras la pantalla siga abierta.
public class DisplayRefreshService implements AutoCloseable {

    @FunctionalInterface
    public interface SnapshotRequest {

        DisplaySnapshot get() throws IOException;
    }

    @FunctionalInterface
    public interface SubscriptionRequest {

        ChangeSubscription open() throws IOException;
    }

    public interface ChangeSubscription extends AutoCloseable {

        boolean awaitChange() throws IOException;

        @Override
        void close() throws IOException;
    }

    private final SnapshotRequest request;
    private final SubscriptionRequest subscriptions;
    private final Consumer<DisplaySnapshot> onSuccess;
    private final Consumer<Exception> onFailure;
    private final ExecutorService worker;
    private volatile ChangeSubscription activeSubscription;
    private volatile boolean closed;
    private boolean started;

    public DisplayRefreshService(
            SnapshotRequest request,
            SubscriptionRequest subscriptions,
            Consumer<DisplaySnapshot> onSuccess,
            Consumer<Exception> onFailure
    ) {
        this.request = Objects.requireNonNull(request, "La consulta es obligatoria.");
        this.subscriptions = Objects.requireNonNull(subscriptions, "La suscripción es obligatoria.");
        this.onSuccess = Objects.requireNonNull(onSuccess, "La acción de éxito es obligatoria.");
        this.onFailure = Objects.requireNonNull(onFailure, "La acción de error es obligatoria.");
        this.worker = Executors.newVirtualThreadPerTaskExecutor();
    }

    public synchronized void start() {
        if (closed || started) {
            throw new IllegalStateException("La actualización no se puede iniciar nuevamente.");
        }
        started = true;
        worker.submit(this::listen);
    }

    // Abre la suscripción antes de consultar la captura para cubrir cambios durante la conexión.
    // Los latidos mantienen el canal; solo los avisos de cambio provocan otra consulta.
    private void listen() {
        while (!closed && !Thread.currentThread().isInterrupted()) {
            try (ChangeSubscription subscription = subscriptions.open()) {
                activeSubscription = subscription;
                if (closed) {
                    return;
                }

                refresh();
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    if (subscription.awaitChange() && !closed) {
                        refresh();
                    }
                }

            } catch (IOException | RuntimeException exception) {
                if (!closed) {
                    onFailure.accept(exception);
                }
            } finally {
                activeSubscription = null;
            }

            if (!closed) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void refresh() throws IOException {
        DisplaySnapshot snapshot = Objects.requireNonNull(
                request.get(), "El servidor no devolvió una captura."
        );
        if (!closed) {
            onSuccess.accept(snapshot);
        }
    }

    // Interrumpe el trabajo y cierra el socket activo para desbloquear una lectura pendiente.
    @Override
    public synchronized void close() {
        closed = true;
        worker.shutdownNow();
        ChangeSubscription subscription = activeSubscription;
        if (subscription != null) {
            try {
                subscription.close();
            } catch (IOException exception) {
                System.err.println("No se pudo cerrar el canal de avisos. Detalle: " + exception);
            }
        }
    }
}
