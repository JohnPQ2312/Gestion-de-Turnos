package cr.ac.una.repository;

import cr.ac.una.model.ActiveDisplaySnapshot;
import cr.ac.una.model.DisplaySnapshot;

// Define las capturas públicas con límites de resultados.
public interface DisplayRepository {

    // Formato anterior (V1/V2): espera y llamados recientes, incluidos finalizados.
    // Lo conservan las herramientas de concurrencia.
    DisplaySnapshot findSnapshot(int waitingLimit, int recentCallsLimit);

    // Pantalla pública: solo turnos activos. Devuelve todos los LLAMADO/EN_ATENCION (cada
    // asignación tiene como máximo uno) y limita únicamente la espera, filtrada por estado antes
    // del límite, junto con el total en espera.
    ActiveDisplaySnapshot findActiveSnapshot(int waitingLimit);
}
