# Contraste de la exportación de base de datos

Revisión: 12 de noviembre de 2026. Referencia documental y de código: `5016e64`.

Archivo revisado: `gamabasis_p3g9 (1).sql`, proporcionado por John. SHA-256: `fcd36db7b4359858dd90ec1ae4bccc210b32c4eb171d2f3cb7791a2748082086`.

La revisión fue estática: se leyeron las sentencias y los datos sin ejecutarlos, importar tablas ni consultar la base activa. La exportación no se incorpora al repositorio: incluye hashes de credenciales existentes. Este informe no reproduce esos hashes ni datos personales de sus filas.

## Resultado

La exportación coincide con el modelo de diez tablas y once relaciones, pero **no es equivalente al esquema entregable**: carece de los dieciséis CHECK y omite las opciones de motor, charset y collation en siete tablas. Tampoco contiene un ADMIN activo con credencial que permita demostrar la administración después de restaurarla.

No se modifican los diagramas para acomodarlos a esas omisiones. El [DDL versionado](../database/script%20borrador.sql) sigue siendo la referencia del modelo documentado. Que una restricción no esté en el archivo no demuestra que falte en la base activa; hace falta consultar esa base o conseguir una exportación completa para determinarlo.

## Estructura contrastada

| Elemento | Resultado |
| --- | --- |
| Tablas | Las mismas diez tablas; ninguna adicional ni ausente. |
| Columnas | Coinciden los nombres y no faltan campos. Se conservan prioridad, asignación, fechas y metadatos de rellamado. |
| Tipos | Compatibles en los elementos revisados. `BOOLEAN` aparece como `tinyint(1)`, representación de MySQL, y algunos ceros tienen comillas; esas diferencias no son campos nuevos. |
| Fechas | Se conserva `TIMESTAMP(6)` para primer/último llamado, inicio y fin. |
| Claves primarias y únicas | Se conservan las identidades, parejas de habilitación, código/consecutivo y exclusiones por columnas calculadas. |
| Columnas calculadas | Se mantienen las expresiones `active_operator_id`, `active_window_id` y `active_assignment_id`. Las inserciones no intentan escribir directamente en ellas. |
| Claves foráneas | Coinciden las once parejas de columnas de referencia. |
| Índices de consulta | Están `idx_ticket_assignment`, `idx_ticket_created_at` e `idx_ticket_last_called`. |
| CHECK | El DDL contiene 16; el archivo exportado no contiene ninguno. |
| Opciones de tabla | Solo tres tablas conservan explícitamente `ENGINE=InnoDB`, `utf8mb4` y `utf8mb4_0900_ai_ci`. |
| Contexto de importación | Incluye `SET NAMES` y zona de sesión UTC. No incluye `CREATE DATABASE` ni `USE`; debe seleccionarse la base destino. |

### Restricciones omitidas

| Tabla | CHECK presentes en el entregable y ausentes en la exportación |
| --- | --- |
| `operator` | `ck_operator_role`, `ck_operator_state` |
| `operator_credential` | `ck_operator_credential_format` |
| `service` | `ck_service_state`, `ck_service_prefix_nonempty` |
| `window` | `ck_window_state`, `ck_window_attention_mode` |
| `operator_window_assignment` | `ck_assignment_time_order` |
| `app_user` | `ck_user_state` |
| `ticket` | `ck_ticket_number`, `ck_ticket_priority`, `ck_ticket_state`, `ck_ticket_assignment`, `ck_ticket_state_times`, `ck_ticket_time_order`, `ck_ticket_call_history` |

Esas restricciones controlan, entre otras cosas, estados permitidos, números positivos, presencia de asignación, fechas y metadatos de rellamado. Conservar las FK y los índices únicos no las sustituye.

Las tablas sin opciones explícitas son `app_user`, `operator`, `operator_credential`, `operator_window_assignment`, `service`, `ticket` y `window`. Al restaurarlas, su motor y codificación dependerían de los valores del destino. `SET NAMES` establece la codificación de la conexión, pero no especifica la collation de cada tabla. Una diferencia con las tablas que sí la declaran puede afectar las comparaciones o la creación de FK de texto. La revisión no atribuye esta omisión a una opción concreta de phpMyAdmin ni a un defecto de la base activa.

## Datos de la exportación

| Tabla | Filas exportadas |
| --- | ---: |
| `app_user` | 0 |
| `operator` | 3 |
| `operator_credential` | 2 |
| `operator_service` | 6 |
| `operator_window_assignment` | 3 |
| `service` | 3 |
| `ticket` | 14 |
| `ticket_sequence` | 3 |
| `window` | 3 |
| `window_service` | 7 |

En las comprobaciones estáticas realizadas no se detectaron referencias huérfanas, códigos visuales o consecutivos duplicados, dos asignaciones abiertas para el mismo recurso ni turnos activos duplicados por asignación. Los estados, presencia y orden de fechas de tickets, códigos visuales y consecutivos revisados resultaron coherentes. Los contadores de servicio no quedaron por debajo de los números emitidos.

Hay nueve tickets FINALIZADO y cinco EN_ESPERA; dos asignaciones permanecen abiertas. Las dos credenciales tienen formato compatible con el componente PBKDF2 del proyecto, pero eso no prueba que se conozcan las contraseñas ni sustituye un ensayo de acceso. **No existe en esta exportación un ADMIN activo con credencial.**

Los registros difieren de los datos iniciales: la exportación conserva operaciones de pruebas anteriores, tres funcionarios y tres ventanillas; el seed prepara cinco funcionarios, cuatro ventanillas y ningún turno ni contraseña. Esta diferencia de contenido no representa por sí sola una divergencia del esquema. La ausencia de `app_user` también es compatible con la emisión actual, que admite turnos sin registro de persona.

## Uso correcto en el entregable

1. Mantener el DDL y los [datos iniciales](../database/datos_prueba.sql) como instalación limpia. El ADMIN inicial recibe una contraseña elegida localmente mediante [CredentialSetup](../database/INICIALIZAR_ACCESO.md); no se distribuyen contraseñas ni hashes de la base compartida.
2. Conservar esta exportación como copia privada de sus datos, sin sustituir con ella los scripts del repositorio ni publicarla automáticamente.
3. Si se necesita entregar un respaldo restaurable del estado compartido, obtener una exportación que conserve también CHECK y opciones de todas las tablas. Contrastar su estructura con `SHOW CREATE TABLE` o con el DDL y probar la restauración en una base desechable antes de declararla válida.
4. Comprobar allí el acceso administrativo, las relaciones, los consecutivos y un recorrido completo. La lectura estática realizada aquí no certifica que esa importación funcione.

El contraste no requiere agregar estados, modificar las relaciones de los diagramas o cambiar Java. Tampoco autoriza reinicializar la base compartida.
