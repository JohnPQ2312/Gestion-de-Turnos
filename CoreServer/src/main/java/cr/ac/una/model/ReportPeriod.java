package cr.ac.una.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Rango de fechas de un reporte, con ambos días incluidos.
 *
 * Las consultas usan [inicio del primer día, inicio del día siguiente al
 * último), de modo que el último día se incluye completo y ningún registro
 * queda fuera por la hora 00:00.
 *
 * No hay un límite de duración del rango. Solo se exige que ambas fechas
 * estén dentro de lo que admiten las columnas TIMESTAMP de MySQL (de
 * 1970-01-01 00:00:01 a 2038-01-19 03:14:07 UTC). Se deja un día de margen
 * en cada extremo para cualquier zona horaria, lo que además garantiza que
 * calcular el día siguiente a la fecha final nunca se desborde.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    public static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 2);
    public static final LocalDate MAX_DATE = LocalDate.of(2038, 1, 17);

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ReportPeriod {
        Objects.requireNonNull(from, "La fecha inicial es obligatoria.");
        Objects.requireNonNull(to, "La fecha final es obligatoria.");

        requireSupported(from, "inicial");
        requireSupported(to, "final");

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial no puede ser posterior a la fecha final."
            );
        }
    }

    public LocalDateTime startInclusive() {
        return from.atStartOfDay();
    }

    public LocalDateTime endExclusive() {
        return to.plusDays(1).atStartOfDay();
    }

    private static void requireSupported(LocalDate date, String label) {
        if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
            throw new IllegalArgumentException(
                    "La fecha " + label + " debe estar entre " + MIN_DATE.format(DISPLAY)
                    + " y " + MAX_DATE.format(DISPLAY)
                    + ", el rango permitido para estas consultas."
            );
        }
    }
}
