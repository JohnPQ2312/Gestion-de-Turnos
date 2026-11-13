package cr.ac.una.server;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Distribuye avisos de cambio a las pantallas suscritas sin enviar el estado completo del
// sistema.
public class DisplayEventHub {

    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    public Subscription subscribe() {
        Subscription subscription = new Subscription();
        subscriptions.add(subscription);
        return subscription;
    }

    // Notifica a cada suscriptor sin esperar a que la pantalla consuma el aviso.
    public void publishChange() {
        for (Subscription subscription : subscriptions) {
            subscription.signalChange();
        }
    }

    public final class Subscription implements AutoCloseable {

        private final BlockingQueue<Boolean> changes = new ArrayBlockingQueue<>(1);
        private volatile boolean closed;

        private Subscription() {
        }

        // Una cola de capacidad uno agrupa cambios próximos; la pantalla consultará el estado
        // vigente.
        private void signalChange() {
            if (!closed) {
                changes.offer(Boolean.TRUE);
            }
        }

        // Espera un cambio hasta diez segundos; sin aviso permite enviar un latido al cliente.
        public boolean awaitChange() throws InterruptedException {
            if (closed) {
                return false;
            }
            Boolean changed = changes.poll(10, TimeUnit.SECONDS);
            return !closed && Boolean.TRUE.equals(changed);
        }

        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            subscriptions.remove(this);
            changes.clear();
            changes.offer(Boolean.FALSE);
        }
    }
}
