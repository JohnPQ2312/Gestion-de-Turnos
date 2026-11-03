# Gestión de turnos

Sistema de gestión de turnos en Java, organizado en cuatro proyectos Maven que se abren por separado en NetBeans.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Entidades, reglas de atención y repositorios. Actualmente conserva los datos en memoria. |
| `CostumerTurn` | Pantalla de solicitud de turnos y confirmación. |
| `OperatorSystem` | Pantalla de asignación de operador/ventanilla y llamado de turnos. |
| `QueueDisplayScreen` | Pantalla pública de turnos. |

## Estado actual

La lógica de negocio se trasladó desde `CostumerTurn` a `CoreServer`. Las pantallas y sus controladores quedaron en sus aplicaciones correspondientes. La presentación y la comunicación con el servidor corresponden a los clientes. Los repositorios, las colas y los consecutivos permanecen en `CoreServer`.

`CoreServer` escucha conexiones TCP locales en el puerto 5000 y procesa las solicitudes de catálogo y generación de turnos. `CostumerTurn` consulta los servicios y solicita turnos mediante sockets; estas operaciones se ejecutan en segundo plano para mantener la interfaz disponible. Dos generadores independientes comparten los consecutivos del mismo servidor.

`OperatorSystem` y `QueueDisplayScreen` conservan sus pantallas, pendientes de conectar al servidor. El llamado todavía está deshabilitado en la aplicación del operador y la pantalla pública abre sin datos.

Las reglas existentes se conservan en el servidor: consecutivos por servicio, atención regular/preferencial, compatibilidad entre servicios del operador y la ventanilla, control de ocupación, estados y tiempos del turno. Sus repositorios en memoria no conservan datos al cerrar el proceso.

## Ejecución

- Utilizar JDK 21 o superior y abrir cada carpeta de proyecto en NetBeans.
- Los tres clientes utilizan JavaFX 13, con sus FXML ajustados a la misma versión. Java y JavaFX tienen versiones independientes.
- Iniciar primero `CoreServer` con su clase `cr.ac.una.CoreServer.App` y mantenerlo ejecutándose.
- Ejecutar `CostumerTurn` con **Run Project**. Se conecta a `127.0.0.1:5000` y carga los servicios automáticamente.
- Si falla la carga del catálogo, iniciar el servidor y utilizar **Reconectar** en el generador.
- Para comprobar los consecutivos compartidos, abrir dos instancias de `CostumerTurn` sin reiniciar `CoreServer` y generar turnos del mismo servicio.
- `OperatorSystem` y `QueueDisplayScreen` pueden abrirse por separado para visualizar sus pantallas; su comunicación está pendiente.

## Base de datos y continuación

El uso de MySQL está autorizado por el profesor, según la aclaración del grupo. El borrador de estructura está en `database/script borrador.sql`; las aplicaciones todavía no están conectadas a una base de datos. Los turnos y consecutivos actuales se pierden al reiniciar el servidor.

El siguiente bloque consiste en conectar los llamados de `OperatorSystem` con `CoreServer` y enviar las actualizaciones a `QueueDisplayScreen`. La persistencia en MySQL continúa pendiente.
