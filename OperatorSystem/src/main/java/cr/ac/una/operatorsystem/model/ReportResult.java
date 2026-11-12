package cr.ac.una.operatorsystem.model;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de una consulta listo para mostrarse en una tabla.
 *
 * El cliente ya convirtió fechas y duraciones a texto legible; la vista solo
 * pinta columnas y filas.
 */
public record ReportResult(
        List<String> columns,
        List<List<String>> rows,
        String summary
        ) {

    public ReportResult {
        columns = List.copyOf(Objects.requireNonNull(columns, "Las columnas son obligatorias."));
        rows = rows.stream().map(List::copyOf).toList();
        summary = summary == null ? "" : summary;

        for (List<String> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException(
                        "Cada fila debe tener un valor por columna."
                );
            }
        }
    }
}
