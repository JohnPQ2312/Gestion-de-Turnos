# Gestión de turnos

Sistema distribuido en Java para emitir turnos, atenderlos por ventanilla y mostrar los llamados en una pantalla pública. Las cuatro aplicaciones son proyectos Maven independientes que se abren en NetBeans. Solo CoreServer accede a MySQL.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Comunicación, permisos, reglas de atención y persistencia JDBC. |
| `CostumerTurn` | Selección de servicio y prioridad, emisión y confirmación del turno. |
| `OperatorSystem` | Acceso del personal, atención, consultas y administración. |
| `QueueDisplayScreen` | Último llamado, ventanilla, llamados recientes, espera y reloj. |

## Funciones disponibles

Los turnos tienen un consecutivo independiente por servicio. La creación del ticket y la actualización de ese número se confirman en una misma transacción. La prioridad se guarda por separado del código visual.

Cada funcionario atiende sus propias asignaciones abiertas. El llamado comprueba actividad, ocupación y servicios habilitados tanto para el funcionario como para la ventanilla. Entre los turnos compatibles se atienden primero los preferenciales y luego los regulares; dentro de cada prioridad se ordenan por fecha de creación e identificador. Las ventanillas mixtas atienden ambos tipos y las `PRIORITY_ONLY` solo preferenciales. Esta prioridad estricta no garantiza una espera máxima para los regulares.

El ciclo es `EN_ESPERA`, `LLAMADO`, `EN_ATENCION` y `FINALIZADO`. Finalizar libera la asignación y conserva el historial. **Volver a llamar** mantiene el mismo turno y su primer llamado, actualiza el último llamado y aumenta el contador. La pausa impide nuevos llamados y rellamados; permite completar el turno activo.

La pantalla pública recibe avisos por una suscripción al servidor y consulta una captura actualizada después de cada cambio confirmado. Muestra hasta 50 turnos en espera y cinco llamados recientes. Si pierde la conexión, conserva los datos, avisa que están desactualizados y vuelve a sincronizarse al reconectar. Las modificaciones directas en MySQL no emiten esos avisos.

El perfil ADMIN dispone de cinco consultas: atenciones entre fechas, cantidades por funcionario y servicio, tiempos de espera y tiempos de atención. También gestiona funcionarios, credenciales, usuarios relacionados con tickets, habilitaciones de servicios e historial de asignaciones. Los usuarios de `app_user` son registros opcionales de personas atendidas; no son las cuentas de acceso del personal.

## Instalación y uso

Se requiere JDK 21 o superior, NetBeans con soporte Maven y MySQL 8. Los clientes incorporan JavaFX 13 mediante Maven.

1. Seguir [INSTALACION.md](INSTALACION.md) para preparar una base vacía, los datos iniciales y las primeras credenciales. Sobre una base existente, aplicar solo las migraciones necesarias.
2. Configurar MySQL únicamente en `CoreServer/src/main/resources/db.properties`. El archivo es privado y está excluido de Git.
3. Ejecutar primero CoreServer y después los tres clientes mediante **Run Project** en NetBeans.
4. Para atender, iniciar sesión, seleccionar una asignación propia y utilizar **Llamar siguiente**, **Iniciar atención** y **Finalizar atención**.

La ejecución predeterminada utiliza `127.0.0.1:5000`. Las conexiones entre computadoras requieren TLS y certificados según [RED_Y_TLS.md](RED_Y_TLS.md). No hay contraseñas predeterminadas.

Las operaciones de red se ejecutan en segundo plano. Si una escritura pierde su confirmación, consultar el estado persistido o el historial antes de repetirla: una espera agotada no demuestra que haya fallado. Reiniciar CoreServer termina las sesiones, pero conserva los turnos y los consecutivos de MySQL.

## Documentación y pruebas

- [Documento técnico y diagramas](docs/README.md).
- [Enunciado del proyecto](docs/enunciado/Proyecto_Programacion_III.pdf).
- [Acceso y sesiones](database/INICIALIZAR_ACCESO.md).
- [Administración de personas](database/ADMINISTRAR_PERSONAS.md).
- [Habilitaciones y asignaciones](database/CONFIGURAR_ATENCION.md).
- [Rellamado y migración](database/RELLAMADO.md).
- [Herramientas de concurrencia y alcance de las comprobaciones](pruebas/README.md).

Se comprobaron instalación, recorrido de atención, concurrencia, permisos, administración, notificaciones y transporte TLS en entornos locales. También hay comprobaciones funcionales de generación, llamado y rellamado sobre la base compartida. Los procedimientos de pruebas distinguen ese alcance del despliegue entre computadoras.

## Alcance pendiente

Faltan integrar y comprobar la administración de servicios y ventanillas, realizar el ensayo físico entre computadoras y medir el rendimiento con la base remota. También quedan la revisión de fechas históricas anteriores al ajuste horario y el cierre del documento técnico, la presentación y el paquete de entrega. Los diagramas deberán contrastarse con la integración final.
