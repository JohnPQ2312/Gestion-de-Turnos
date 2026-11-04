package cr.ac.una.server;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DisplayEventHub {

    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    public Subscription subscribe() {
        Subscription subscription = new Subscription();
        subscriptions.add(subscription);
        return subscription;
    }

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

        private void signalChange() {
            if (!closed) {
                changes.offer(Boolean.TRUE);
            }
        }

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
