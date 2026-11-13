package cr.ac.una.config;

// Mide los intentos de conexión a MySQL de la solicitud actual.
// ThreadLocal separa éxitos, fallos y tiempos de los hilos que atienden otras solicitudes.
public final class ConnectionMetrics {

    private static final int SUCCESSFUL = 0;
    private static final int FAILED = 1;
    private static final int NANOS = 2;

    private static final ThreadLocal<long[]> CURRENT
            = ThreadLocal.withInitial(() -> new long[3]);

    private ConnectionMetrics() {
    }

    // Reinicia la medición al comenzar una solicitud.
    public static void reset() {
        long[] values = CURRENT.get();
        values[SUCCESSFUL] = 0;
        values[FAILED] = 0;
        values[NANOS] = 0;
    }

    // Lo invoca DatabaseConnectionFactory cuando obtiene una conexión.
    static void recordSuccess(long elapsedNanos) {
        long[] values = CURRENT.get();
        values[SUCCESSFUL]++;
        values[NANOS] += elapsedNanos;
    }

    // Lo invoca DatabaseConnectionFactory cuando no logra abrir una conexión.
    static void recordFailure(long elapsedNanos) {
        long[] values = CURRENT.get();
        values[FAILED]++;
        values[NANOS] += elapsedNanos;
    }

    public static long successfulOpenings() {
        return CURRENT.get()[SUCCESSFUL];
    }

    public static long failedOpenings() {
        return CURRENT.get()[FAILED];
    }

    // Tiempo total de los intentos de apertura, exitosos y fallidos.
    public static long openingMillis() {
        return CURRENT.get()[NANOS] / 1_000_000;
    }
}