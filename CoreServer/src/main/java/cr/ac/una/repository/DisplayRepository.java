package cr.ac.una.repository;

import cr.ac.una.model.DisplaySnapshot;

// Define la captura pública de turnos en espera y llamados recientes, con límites de resultados.
public interface DisplayRepository {

    DisplaySnapshot findSnapshot(int waitingLimit, int recentCallsLimit);
}
