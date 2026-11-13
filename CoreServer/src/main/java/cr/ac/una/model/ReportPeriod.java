package cr.ac.una.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

// Incluye los dos días elegidos para el reporte mediante un intervalo de inicio incluido y fin
// excluido.
// El fin corresponde al inicio del día siguiente, para incluir completo el último día.
// Los límites dejan margen al rango TIMESTAMP de MySQL y al cálculo del día siguiente;
// no se impone una duración máxima adicional al período.
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

    // Usa el comienzo del día siguiente para incluir todas las horas de la fecha final.
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
