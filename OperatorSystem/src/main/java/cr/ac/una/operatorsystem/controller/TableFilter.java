package cr.ac.una.operatorsystem.controller;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

// Filtro local de texto para una tabla administrativa.
// Ignora mayúsculas y tildes, exige todas las palabras escritas y no consulta al servidor.
// La fila seleccionada nunca desaparece por el filtro: el formulario que la acompaña siempre
// corresponde a una fila visible. Si esa fila no coincide, se muestra marcada en el resumen y
// deja de verse al elegir otra o limpiar la selección. Escape borra el texto del filtro.
// Origen -> filtro -> orden: la tabla conserva la ordenación por encabezados de columna porque su
// comparador se enlaza a la lista ordenada; ordenar solo reordena filas, no consulta nada.
final class TableFilter<T> {
    private final TableView<T> table;
    private final TextField field;
    private final Label summary;
    private final Function<T, String> searchText;
    private final ObservableList<T> source = FXCollections.observableArrayList();
    private final FilteredList<T> visible = new FilteredList<>(source);
    private final SortedList<T> sorted = new SortedList<>(visible);
    private final Node originalPlaceholder;
    private final Label noMatches = new Label();
    private List<String> words = List.of();
    private T pinned;
    // Al refiltrar, TableView anuncia de forma transitoria que la selección pasó a null y volvió a
    // la misma fila. Esos avisos no son una acción de la persona: los controladores los ignoran
    // con isRefiltering() para no reiniciar su formulario, y este filtro no los procesa.
    private boolean refreshing;
    private boolean refreshQueued;

    TableFilter(TableView<T> table, TextField field, Label summary, Function<T, String> searchText) {
        this.table = Objects.requireNonNull(table);
        this.field = Objects.requireNonNull(field);
        this.summary = Objects.requireNonNull(summary);
        this.searchText = Objects.requireNonNull(searchText);
        this.originalPlaceholder = table.getPlaceholder();
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        field.textProperty().addListener((observable, before, text) -> {
            words = split(text);
            refresh();
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && !field.getText().isEmpty()) {
                field.clear();
                event.consume();
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, before, row) -> {
            if (refreshing || Objects.equals(pinned, row)) {
                return;
            }
            pinned = row;
            // Modificar la lista dentro del propio aviso de selección deja la selección vacía:
            // el refiltrado se aplaza al siguiente pulso. Sin texto de filtro no hay nada que ocultar.
            if (!words.isEmpty() && !refreshQueued) {
                refreshQueued = true;
                Platform.runLater(() -> {
                    refreshQueued = false;
                    refresh();
                });
            }
        });
        refresh();
    }

    // Reemplaza todas las filas conservando el texto del filtro. Como TableView limpia la
    // selección al cambiar el origen, el llamador vuelve a seleccionar con select().
    void setAll(Collection<T> rows) {
        pinned = null;
        source.setAll(rows);
        // Según las capas de listas, TableView puede conservar la selección de una fila igual a la
        // anterior: se limpia siempre, como al reemplazar el contenido de una lista simple.
        table.getSelectionModel().clearSelection();
        refresh();
    }

    // Selecciona una fila asegurando que sea visible aunque no coincida con el filtro.
    void select(T row) {
        pinned = row;
        refresh();
        table.getSelectionModel().select(row);
    }

    // Verdadero mientras la tabla se refiltra: los avisos de selección de ese momento son transitorios.
    boolean isRefiltering() {
        return refreshing;
    }

    int totalRows() {
        return source.size();
    }

    int visibleRows() {
        return visible.size();
    }

    private void refresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            visible.setPredicate(row -> words.isEmpty() || isPinned(row) || matches(row));
        } finally {
            refreshing = false;
        }
        long real = words.isEmpty() ? source.size() : source.stream().filter(this::matches).count();
        boolean pinnedExtra = !words.isEmpty() && pinned != null && source.contains(pinned) && !matches(pinned);
        if (words.isEmpty()) {
            summary.setText("");
        } else if (real == 0) {
            summary.setText(pinnedExtra ? "Sin coincidencias · se mantiene la fila seleccionada" : "Sin coincidencias");
        } else {
            summary.setText(real + " de " + source.size() + (pinnedExtra ? " · incluye la fila seleccionada" : ""));
        }
        if (!words.isEmpty() && real == 0 && !pinnedExtra && !source.isEmpty()) {
            noMatches.setText("Ningún registro coincide con «" + field.getText().trim() + "».");
            table.setPlaceholder(noMatches);
        } else {
            table.setPlaceholder(originalPlaceholder);
        }
    }

    private boolean isPinned(T row) {
        return pinned != null && pinned.equals(row);
    }

    private boolean matches(T row) {
        String text = fold(searchText.apply(row));
        return words.stream().allMatch(text::contains);
    }

    private static List<String> split(String text) {
        String folded = fold(text == null ? "" : text).trim();
        return folded.isEmpty() ? List.of() : List.of(folded.split("\\s+"));
    }

    // Minúsculas sin tildes ni otras marcas combinadas: «María» y «maria» se consideran iguales.
    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
