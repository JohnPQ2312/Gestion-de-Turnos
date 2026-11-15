# Gestión de turnos

Sistema distribuido en Java para emitir turnos, atenderlos por ventanilla y mostrar los llamados en una pantalla pública. Las cuatro aplicaciones son proyectos Maven independientes que se abren en NetBeans. Solo CoreServer accede a MySQL.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Comunicación, permisos, reglas de atención y persistencia JDBC. |
| `CostumerTurn` | Selección de servicio y prioridad, emisión y confirmación del turno. |
| `OperatorSystem` | Acceso del personal, atención, consultas y administración. |
| `QueueDisplayScreen` | Turno llamado con su ventanilla, lista de turnos activos (llamados, en atención y en espera) y reloj. |

## Funciones disponibles

Los turnos tienen un consecutivo independiente por servicio. La creación del ticket y la actualización de ese número se confirman en una misma transacción. La prioridad se guarda por separado del código visual.

Cada funcionario atiende sus propias asignaciones abiertas y el servidor solo le entrega las suyas (`LIST_ASSIGNMENTS`, una consulta). El llamado comprueba actividad, ocupación y servicios habilitados tanto para el funcionario como para la ventanilla. Entre los turnos compatibles se atienden primero los preferenciales y luego los regulares; dentro de cada prioridad se ordenan por fecha de creación e identificador. Las ventanillas mixtas atienden ambos tipos y las `PRIORITY_ONLY` solo preferenciales. Esta prioridad estricta no garantiza una espera máxima para los regulares. La atención preferencial con ventanilla exclusiva es la característica adicional del proyecto: reserva una ventanilla para quienes la necesitan y las mixtas apoyan cuando hay espera.

El ciclo es `EN_ESPERA`, `LLAMADO`, `EN_ATENCION` y `FINALIZADO`. Finalizar libera la asignación y conserva el historial. **Volver a llamar** mantiene el mismo turno y su primer llamado, actualiza el último llamado y aumenta el contador. La pausa impide nuevos llamados y rellamados; permite completar el turno activo.

La pantalla pública recibe avisos por una suscripción al servidor y consulta una captura actualizada (`GET_DISPLAY_SNAPSHOT_V3`) después de cada cambio confirmado. Tiene dos zonas. La superior anuncia el llamado activo más reciente con su ventanilla: mientras ese turno está en atención sigue anunciado, y solo lo sustituye otro llamado más reciente. El aviso «Volvemos a llamar» corresponde a un turno que sigue en estado llamado. La inferior lista los turnos activos con turno, servicio, estado y ventanilla: todos los llamados y en atención, ordenados por último llamado, y hasta 30 en espera, con una nota cuando hay más. Los turnos finalizados no aparecen. Si las filas no caben, la lista cambia de página cada ocho segundos. Si pierde la conexión, conserva los datos, avisa que están desactualizados y vuelve a sincronizarse al reconectar. Las modificaciones directas en MySQL no emiten esos avisos. El servidor conserva `GET_DISPLAY_SNAPSHOT` y `GET_DISPLAY_SNAPSHOT_V2` para herramientas anteriores; la pantalla actual usa V3.

El perfil ADMIN dispone de cinco consultas: atenciones entre fechas, cantidades por funcionario y servicio, tiempos de espera y tiempos de atención. También gestiona funcionarios, credenciales, usuarios relacionados con tickets, habilitaciones de servicios e historial de asignaciones. La vista Servicios y ventanillas permite crear y editar el catálogo, dar de baja registros y reactivarlos sin borrar su historial. Los usuarios de `app_user` son registros opcionales de personas atendidas; no son las cuentas de acceso del personal. En OperatorSystem, Personas, Asignaciones y Servicios y ventanillas se abren desde el menú Administración; Consultas y Cerrar sesión quedan a la vista.

CostumerTurn carga el catálogo al abrirse y lo vuelve a cargar al terminar cada turno. El botón «Actualizar servicios» permite recargarlo en cualquier momento. Solo la lista de servicios se desplaza; el panel de prioridad y el botón de emisión permanecen fijos.

## Instalación y uso

Se requiere JDK 21 o superior, NetBeans con soporte Maven y MySQL 8. Los clientes incorporan JavaFX 13 mediante Maven.

1. Con la base que el docente asignó al grupo, seguir [INSTALACION.md](INSTALACION.md). Para crear una base propia con sus datos iniciales y primeras credenciales, seguir [INSTALACION_BASE_PROPIA.md](INSTALACION_BASE_PROPIA.md).
2. Configurar MySQL únicamente en `CoreServer/src/main/resources/db.properties`, que se copia de `db.properties.example`. El archivo es privado y está excluido de Git, así que un clon nuevo no lo trae.
3. Ejecutar primero CoreServer y después los tres clientes mediante **Run Project** en NetBeans.
4. Para atender, iniciar sesión, seleccionar una asignación propia y utilizar **Llamar siguiente**, **Iniciar atención** y **Finalizar atención**.

La ejecución predeterminada utiliza `127.0.0.1:5000`. Las conexiones entre computadoras requieren TLS y certificados según [RED_Y_TLS.md](RED_Y_TLS.md). No hay contraseñas predeterminadas.

Las operaciones de red se ejecutan en segundo plano. Si una escritura pierde su confirmación, consultar el estado persistido o el historial antes de repetirla: una espera agotada no demuestra que haya fallado. Reiniciar CoreServer termina las sesiones, pero conserva los turnos y los consecutivos de MySQL.

## Documentación y pruebas

- [Documento técnico y diagramas](docs/README.md).
- [Presentación para la defensa](docs/presentacion.pptx).
- [Enunciado del proyecto](docs/enunciado/Proyecto_Programacion_III.pdf).
- [Acceso y sesiones](database/INICIALIZAR_ACCESO.md).
- [Administración de personas](database/ADMINISTRAR_PERSONAS.md).
- [Habilitaciones y asignaciones](database/CONFIGURAR_ATENCION.md).
- [Administración de servicios y ventanillas](database/ADMINISTRAR_CATALOGO.md).
- [Rellamado y migración](database/RELLAMADO.md).
- [Herramientas de concurrencia y alcance de las comprobaciones](pruebas/README.md).

## Verificación y alcance

Se comprobó el código actual con un escenario integrado de 64 comprobaciones (CoreServer real contra MySQL local desechable y las cuatro aplicaciones conducidas por sus controles, en ventanas fuera de pantalla), 21 comprobaciones del anuncio de la pantalla pública y 15 de la pérdida de la base de datos con el sistema en marcha. También se ejecutaron las aplicaciones con el código actual sobre la base asignada: emisión, llamado, rellamado, inicio y finalización de un turno. Antes de los últimos cambios se había recorrido el catálogo sobre la base asignada con interfaces y clientes de protocolo reales. El [resumen de pruebas](pruebas/README.md#comprobaciones-y-límites) precisa el entorno de cada una.

Las aplicaciones se comprobaron en una sola computadora y el escenario integrado se ejecutó por loopback, sin TLS. El despliegue entre computadoras, la medición de rendimiento bajo carga y la revisión de fechas históricas anteriores al ajuste horario quedan como trabajo futuro. En una medición previa, dos llamados remotos tardaron cerca de 6,2 segundos de procesamiento porque cada uno abría siete conexiones; el código actual reduce esas conexiones.
