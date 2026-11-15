package cr.ac.una.QueueDisplayScreen.controller;

import java.util.ArrayList;
import java.util.List;

// Reparte las filas activas de la pantalla pública en páginas, sin JavaFX ni red.
// Si hay espera y los llamados caben dejando al menos minWaitingSlots filas libres, los llamados se repiten
// al inicio de cada página y la espera rota en el espacio restante. Si no, rota la lista completa: primero
// los llamados y después la espera, en el orden recibido. Sin filas no hay páginas.
// El controlador calcula rowsPerPage a partir del alto disponible y presenta la página actual.
final class DisplayPaging {

    private DisplayPaging() {
    }

    static <T> List<List<T>> pages(List<T> called, List<T> waiting, int rowsPerPage, int minWaitingSlots) {
        if (rowsPerPage < 1 || minWaitingSlots < 1) {
            throw new IllegalArgumentException("Las filas por página y el mínimo de espera deben ser positivos.");
        }
        List<List<T>> pages = new ArrayList<>();

        if (!waiting.isEmpty() && called.size() <= rowsPerPage - minWaitingSlots) {
            int slots = rowsPerPage - called.size();
            for (int start = 0; start < waiting.size(); start += slots) {
                List<T> page = new ArrayList<>(called);
                page.addAll(waiting.subList(start, Math.min(waiting.size(), start + slots)));
                pages.add(page);
            }
        } else {
            List<T> all = new ArrayList<>(called);
            all.addAll(waiting);
            for (int start = 0; start < all.size(); start += rowsPerPage) {
                pages.add(all.subList(start, Math.min(all.size(), start + rowsPerPage)));
            }
        }
        return pages;
    }
}
