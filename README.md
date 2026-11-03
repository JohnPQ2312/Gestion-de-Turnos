# Gestión de turnos

Sistema de gestión de turnos en Java, organizado en cuatro proyectos Maven que se abren por separado en NetBeans.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Entidades, reglas de atención, repositorios y servidor TCP. Consulta servicios y guarda turnos y consecutivos en MySQL. |
| `CostumerTurn` | Pantalla de solicitud de turnos y confirmación, conectada al servidor. |
| `OperatorSystem` | Pantalla de asignación de operador/ventanilla y llamado de turnos; conexión pendiente. |
| `QueueDisplayScreen` | Pantalla pública de turnos; conexión pendiente. |

## Estado actual

La lógica de negocio se encuentra en `CoreServer`. Cada cliente contiene su pantalla y controlador. Los clientes se comunican con el servidor; las conexiones a MySQL se realizan desde `CoreServer`.

`CoreServer` escucha conexiones TCP locales en el puerto 5000 y procesa las solicitudes de catálogo y generación de turnos. `CostumerTurn` ejecuta estas solicitudes en segundo plano para mantener la interfaz disponible y muestra la confirmación del turno generado.

El catálogo se consulta mediante `JdbcServiceRepository`. La generación utiliza `JdbcTicketCreationRepository`, que comprueba el servicio activo, bloquea la fila de su consecutivo y guarda el turno y el nuevo consecutivo dentro de una misma transacción. Los turnos se registran como `EN_ESPERA`, con su prioridad y fecha de creación.

Las interfaces separan los contratos de sus implementaciones. `TicketCreationRepository` define la creación de turnos y `TicketRepository` amplía ese contrato con consultas y llamado. Las implementaciones en memoria se conservan para el desarrollo de la atención; la generación activa utiliza MySQL.

`OperatorSystem` y `QueueDisplayScreen` conservan sus pantallas, pendientes de conectar al servidor. El llamado todavía está deshabilitado en la aplicación del operador y la pantalla pública abre sin datos. La lógica de compatibilidad de servicios, ocupación, prioridad y cambios de estado existe en el servidor, pero su integración con estos clientes y su persistencia JDBC continúan pendientes.

## Configuración y ejecución

- Utilizar JDK 21 o superior y abrir cada carpeta de proyecto en NetBeans.
- Los tres clientes utilizan JavaFX 13, con sus FXML ajustados a la misma versión. Java y JavaFX tienen versiones independientes.
- Disponer de la estructura MySQL del proyecto y de los servicios y filas de `ticket_sequence` inicializados. La estructura está en `database/script borrador.sql`.
- Crear localmente `CoreServer/src/main/resources/db.properties` con las propiedades indicadas debajo. Este archivo está excluido de Git.

```properties
db.url=jdbc:mysql://HOST:3306/NOMBRE_BASE
db.usuario=USUARIO
db.clave=CONTRASEÑA_LOCAL
```

- Iniciar primero `CoreServer` con su clase `cr.ac.una.CoreServer.App` y mantenerlo ejecutándose.
- Ejecutar `CostumerTurn` con **Run Project**. Se conecta a `127.0.0.1:5000` y carga los servicios automáticamente.
- Si falla la carga del catálogo, comprobar el servidor y su acceso a MySQL, y utilizar **Reconectar** en el generador.
- `OperatorSystem` y `QueueDisplayScreen` pueden abrirse por separado para visualizar sus pantallas; su comunicación está pendiente.

## Comprobaciones realizadas

- Registro de un turno regular y consulta de su fila y consecutivo en MySQL.
- Continuación de la numeración después de reiniciar `CoreServer`.
- Registro de un turno preferencial y comprobación de su prioridad en MySQL.
- Consecutivos independientes por servicio: Información general y Trámites.
- Generación desde dos ejecuciones de `CostumerTurn` conectadas al mismo servidor, con códigos distintos.

La prueba manual con dos clientes no demuestra por sí sola el comportamiento bajo solicitudes exactamente simultáneas. La comprobación automatizada de concurrencia del repositorio JDBC y la de recuperación ante fallos de transacción siguen pendientes.

## Continuación

Conectar `OperatorSystem` al servidor e implementar la persistencia del llamado y los cambios de estado. Después, enviar las actualizaciones a `QueueDisplayScreen` para mostrar los turnos y las ventanillas correspondientes.
