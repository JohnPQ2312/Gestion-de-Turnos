package cr.ac.una.repository;

import cr.ac.una.model.DisplaySnapshot;

public interface DisplayRepository {

    DisplaySnapshot findSnapshot(int waitingLimit, int recentCallsLimit);
}
