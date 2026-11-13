package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.ManagedService;
import cr.ac.una.operatorsystem.model.ManagedWindow;
import java.io.IOException;
import java.util.List;

// Solicitudes de administración de servicios y ventanillas. Los controladores no conocen el protocolo.
public interface CatalogRequests {
    List<ManagedService> services() throws IOException;
    void createService(String code, String name, String prefix) throws IOException;
    void updateService(String code, String name, String prefix) throws IOException;
    void setServiceActive(String code, boolean active) throws IOException;
    List<ManagedWindow> windows() throws IOException;
    void createWindow(int id, String mode) throws IOException;
    void updateWindowMode(int id, String mode) throws IOException;
    String setWindowActive(int id, boolean active) throws IOException;
}
