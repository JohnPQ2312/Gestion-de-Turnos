# Gestión de turnos

Sistema de gestión de turnos en Java, organizado en cuatro proyectos Maven que se abren por separado en NetBeans.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Entidades, reglas de atención y repositorios. Actualmente conserva los datos en memoria. |
| `CostumerTurn` | Pantalla de solicitud de turnos y confirmación. |
| `OperatorSystem` | Pantalla de asignación de operador/ventanilla y llamado de turnos. |
| `QueueDisplayScreen` | Pantalla pública de turnos. |

## Estado de la reorganización

La lógica de negocio se trasladó desde `CostumerTurn` a `CoreServer`. Las pantallas y sus controladores quedaron en sus aplicaciones correspondientes. Los clientes contienen únicamente datos de presentación y puntos de configuración para las solicitudes; no crean repositorios ni administran colas o consecutivos.

Esta versión corresponde a la reorganización previa a la comunicación entre aplicaciones. Cada cliente abre su pantalla por separado. Generar y llamar permanecen deshabilitados hasta configurar la conexión con el servidor; la pantalla pública abre sin datos. `CoreServer` informa de este estado y termina: todavía no escucha conexiones. El prototipo anterior de tres ventanas en una misma ejecución dejó de ser el arranque de `CostumerTurn`.

Las reglas existentes se conservan en el servidor: consecutivos por servicio, atención regular/preferencial, compatibilidad entre servicios del operador y la ventanilla, control de ocupación, estados y tiempos del turno. Sus repositorios en memoria no conservan datos al cerrar el proceso.

## Ejecución

- Utilizar JDK 21 o superior y abrir cada carpeta de proyecto en NetBeans.
- Los tres clientes utilizan JavaFX 13, con sus FXML ajustados a la misma versión. Java y JavaFX tienen versiones independientes.
- Ejecutar cada cliente con la acción **Run Project** de NetBeans; cada uno muestra su propia pantalla.
- Ejecutar `CoreServer` con su clase `cr.ac.una.CoreServer.App`. Por ahora muestra el estado del núcleo y finaliza.

## Base de datos y continuación

El uso de MySQL está autorizado por el profesor, según la aclaración del grupo. El borrador de estructura está en `database/script borrador.sql`; esta reorganización no lo ejecuta ni conecta las aplicaciones a una base de datos.

El siguiente bloque consiste en conectar `CostumerTurn` con `CoreServer` para solicitar un turno y recibir el código generado. Después se incorporarán los llamados del operador y las actualizaciones de la pantalla pública.
