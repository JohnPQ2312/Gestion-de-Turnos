# Herramientas de concurrencia

Esta prueba prepara dos conexiones a CoreServer y libera sus solicitudes CALL_NEXT desde una misma señal. Comprueba las respuestas y vuelve a leer los turnos activos mediante el servidor. CoreServer ejecuta las operaciones reales sobre la base configurada.

## Preparación

1. Usar JDK 21 o superior e iniciar CoreServer. Cada funcionario de la prueba debe tener una credencial habilitada (ver [inicialización del acceso](../database/INICIALIZAR_ACCESO.md)).
2. Abrir los dos operadores y comprobar las asignaciones del operador 1 con ventanilla 1 y del operador 2 con ventanilla 2.
3. Completar las atenciones existentes para que ambas asignaciones queden libres.
4. Preparar dos turnos preferenciales de Información general como primeros turnos de la lista de espera.
5. Mantener estables las asignaciones y la cola durante la prueba.

`ConcurrentCallingCheck.java` identifica las asignaciones activas mediante LIST_ASSIGNMENTS y consulta GET_ACTIVE_TICKET antes de enviar los llamados. Si los requisitos de preparación no se cumplen, detiene la ejecución. `TestAccess` inicia una sesión propia por funcionario, vincula cada asignación con su cuenta y cierra las sesiones al terminar.

## Ejecución

Desde PowerShell, en la raíz del repositorio y con el JDK disponible en PATH:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
.\pruebas\Ejecutar.ps1 -Prueba ConcurrentCallingCheck
```

El lanzador compila las cuatro clases auxiliares en `pruebas/.build`; los `.class` están excluidos de Git. La prueba utiliza 127.0.0.1:5000. No necesita las credenciales de la base: solicita el usuario y la contraseña de los funcionarios 1 y 2. Cada cuenta solo consulta y utiliza sus propias asignaciones.

Envía dos llamados y deja los turnos llamados para inspeccionarlos. Si se agota la espera, revisar la consola de CoreServer y las asignaciones guardadas antes de repetir. La herramienta no reintenta escrituras automáticamente.

## Acceso y resultados de las herramientas

- Ingresar la cuenta de cada funcionario solicitado. Una cuenta ADMIN no permite hacerse pasar por otro operador.
- La contraseña se oculta cuando Java dispone de consola interactiva. Si no existe, se informa que la entrada será visible. No pasar contraseñas ni tokens como argumentos ni guardarlos en archivos de pruebas.
- Las sesiones se cierran al terminar, incluso ante un error de preparación. Si no se confirma LOGOUT por un corte, se informa y la sesión vence según la política de CoreServer.
- El proceso devuelve código 0 si pasan las comprobaciones y 1 si la prueba falla o se detiene. Se puede consultar `$LASTEXITCODE` en PowerShell.
- No se reintentan escrituras automáticamente. Un tiempo de espera agotado exige revisar el estado antes de repetir.

Ejemplos para las otras pruebas:

```powershell
.\pruebas\Ejecutar.ps1 -Prueba SameAssignmentCallingCheck -Parametros 1,1
.\pruebas\Ejecutar.ps1 -Prueba SingleEligibleTicketCheck -Parametros 1,1,2,2
```

`-Puerto` permite utilizar otro servidor **local**, por ejemplo el 33318 del entorno aislado. No configura los clientes de la aplicación ni cambia la conexión MySQL de CoreServer.

Si se prefiere compilar sin el lanzador, desde la raíz del repositorio:

```powershell
New-Item -ItemType Directory -Path pruebas/.build -Force | Out-Null
javac -encoding UTF-8 --release 21 -d pruebas/.build pruebas/TestAccess.java pruebas/ConcurrentCallingCheck.java pruebas/SameAssignmentCallingCheck.java pruebas/SingleEligibleTicketCheck.java
java -cp pruebas/.build ConcurrentCallingCheck
```

Las herramientas dependen de `TestAccess`; deben compilarse juntas mediante el lanzador o el comando anterior.

## Llamados simultáneos sobre una misma asignación

`SameAssignmentCallingCheck.java` envía dos `CALL_NEXT` sobre **la misma** asignación, liberados por una señal común. Comprueba que el sistema admita un solo llamado y rechace el otro por ocupación, y después verifica el estado guardado mediante el servidor.

### Preparación

Coordinar con el equipo quién prepara los datos y cuándo se ejecuta. Ninguna otra operación debe modificar la asignación ni la cola durante la prueba.

1. Iniciar CoreServer.
2. Elegir una asignación vigente (por defecto, operador 1 en ventanilla 1) y comprobar desde OperatorSystem que no tenga turno activo. Si conserva un turno de una prueba anterior, completar o revisar su atención antes.
3. Tener al menos dos turnos `EN_ESPERA` compatibles con esa asignación y menos de 50 turnos en espera en total.

La herramienta detiene la ejecución si la asignación no existe, no está libre o hay menos de dos turnos en espera. La compatibilidad no se puede consultar por el protocolo; se comprueba antes en MySQL con la consulta siguiente, que debe devolver al menos dos filas:
```sql
SELECT t.ticket_id, t.visual_code, t.`priority`, t.created_at
FROM ticket t
JOIN operator_window_assignment a ON a.assignment_id = 1   -- cambiar 1 por la asignación de la prueba
JOIN operator_service os ON os.operator_id = a.operator_id AND os.service_code = t.service_id
JOIN window_service ws ON ws.window_id = a.window_id AND ws.service_code = t.service_id
JOIN `window` w ON w.window_id = a.window_id
JOIN service s ON s.service_code = t.service_id AND s.service_state = 'ACTIVO'
WHERE t.ticket_state = 'EN_ESPERA'
  AND (w.attention_mode = 'MIXED' OR t.`priority` = TRUE)
ORDER BY t.`priority` DESC, t.created_at, t.ticket_id;
```

### Ejecución

Desde PowerShell, en la raíz del repositorio:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
.\pruebas\Ejecutar.ps1 -Prueba SameAssignmentCallingCheck -Parametros 1,1
```

Los dos números son el operador y la ventanilla de la asignación. No reintenta escrituras.

### Resultado esperado

- Exactamente una respuesta `OK;CALLED` para el operador y la ventanilla de la asignación.
- Exactamente una respuesta `ERROR` con el mensaje de ocupación: «La asignación, el operador o la ventanilla ya tienen un turno activo.» Un error distinto (por ejemplo, de base de datos) cuenta como falla.
- `GET_ACTIVE_TICKET` devuelve el turno llamado, en estado `LLAMADO`.
- El turno llamado ya no aparece en espera, los demás turnos siguen en espera y la espera baja exactamente en uno.

Al final la herramienta imprime dos consultas para confirmar en phpMyAdmin el estado de los turnos involucrados y que la asignación tiene un solo turno activo. Las líneas `[tiempo]` de la consola de CoreServer registran el procesamiento de cada `CALL_NEXT`.

## Un único turno elegible para dos ventanillas

`SingleEligibleTicketCheck.java` envía `CALL_NEXT` desde dos asignaciones distintas, liberados por una señal común, cuando existe un solo turno en espera compatible con ambas. Comprueba que solo una ventanilla obtenga el turno y que la otra reciba `OK;NO_TICKET`.

### Preparación

Coordinar con el equipo quién prepara los datos y cuándo se ejecuta. Nadie debe generar ni llamar turnos durante la prueba.

1. Iniciar CoreServer.
2. Comprobar que las dos asignaciones (por defecto, operador 1 en ventanilla 1 y operador 2 en ventanilla 2) estén libres.
3. Dejar exactamente un turno `EN_ESPERA` en todo el sistema, compatible con ambas asignaciones.

La herramienta se detiene si alguna asignación no existe o no está libre, o si no hay exactamente un turno en espera. La compatibilidad se comprueba antes en MySQL; la consulta siguiente debe devolver dos filas, una por asignación, con el mismo turno:

```sql
SELECT a.assignment_id, a.window_id, t.visual_code, t.`priority`, w.attention_mode
FROM ticket t
JOIN operator_window_assignment a ON a.assignment_id IN (1, 2)   -- cambiar por las asignaciones de la prueba
JOIN operator_service os ON os.operator_id = a.operator_id AND os.service_code = t.service_id
JOIN window_service ws ON ws.window_id = a.window_id AND ws.service_code = t.service_id
JOIN `window` w ON w.window_id = a.window_id
JOIN service s ON s.service_code = t.service_id AND s.service_state = 'ACTIVO'
WHERE t.ticket_state = 'EN_ESPERA'
  AND (w.attention_mode = 'MIXED' OR t.`priority` = TRUE);
```

### Ejecución

Desde PowerShell, en la raíz del repositorio:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
.\pruebas\Ejecutar.ps1 -Prueba SingleEligibleTicketCheck -Parametros 1,1,2,2
```

Los números son operador y ventanilla de la primera asignación y de la segunda. No reintenta escrituras.

### Resultado esperado

- Exactamente una ventanilla recibe `OK;CALLED` con el turno en disputa.
- La otra recibe `OK;NO_TICKET`. Un `ERROR` o un segundo `OK;CALLED` cuenta como falla.
- `GET_ACTIVE_TICKET` muestra el turno `LLAMADO` en la ventanilla ganadora y ningún turno activo en la otra.
- No quedan turnos en espera.

Al final la herramienta imprime dos consultas para confirmar en phpMyAdmin el estado del turno y que cada asignación tiene como máximo un turno activo.


## Comprobaciones y límites

La validación local incluyó los repositorios JDBC, el servidor, los clientes y las vistas JavaFX sobre MySQL 8.0.32 desechable. Se ejecutó con JDK 25.0.3, destino de compilación Java 21 y JavaFX 13. No se probó por separado un runtime JDK 21.

| Área | Comprobaciones realizadas |
| --- | --- |
| Emisión y atención | Consecutivos por servicio, prioridad, compatibilidad, ocupación, recuperación del turno activo y ciclo completo con fechas persistidas. |
| Concurrencia | Generación simultánea, dos asignaciones compitiendo por turnos, dos llamados sobre la misma asignación, un único turno elegible y rollback ante fallos provocados. |
| Acceso y administración | Permisos por rol, sesiones, revocación tras cambios de cuenta, protección del último ADMIN con credencial, bajas lógicas y conservación del historial. |
| Habilitaciones y asignaciones | Compatibilidad compartida, rechazo de retiradas que afectan turnos o dejan sin servicio una asignación, apertura/cierre y conflictos concurrentes. |
| Servicios y ventanillas | Alta con consecutivo atómico, duplicados, prefijo protegido tras emitir, bajas incompatibles, cambios de modalidad, reactivación, rollback y altas simultáneas. |
| Rellamado | Identidad y primer llamado conservados, contador y última fecha actualizados, pausa, estados no permitidos y recuperación tras una confirmación perdida. |
| Pantalla pública | Varias suscripciones, avisos tras cambios confirmados, latidos, sincronización inicial, desconexión y recuperación. |
| Transporte | TLS con clientes reales, nombre de certificado incorrecto, falta de confianza y configuración inválida, sin degradación automática a TCP. |

La instalación se comprobó desde una copia limpia obtenida de los archivos versionados, sin reutilizar los recursos generados o privados de la carpeta del equipo. Se aplicaron esquema y datos iniciales, se prepararon credenciales con `CredentialSetup` y se ejecutó el recorrido por TLS. También se comprobaron clientes en procesos separados en la misma computadora. La compilación utilizó dependencias disponibles en la caché de Maven; no verificó su primera descarga en un equipo sin caché.

Sobre la base compartida se comprobó la generación con persistencia, la continuación de consecutivos, el ciclo de atención y dos llamados coordinados a asignaciones diferentes. Ese ensayo produjo dos turnos distintos y ninguna ventanilla con más de un turno activo. Los llamados tardaron aproximadamente 5,5 y 6,5 segundos; esos tiempos históricos no equivalen a una medición actual bajo carga ni al tiempo de actualización visual.

Después de aplicar las migraciones también se comprobó el rellamado sobre la base compartida: conservó el ticket y el primer llamado, aumentó el contador, actualizó ambas pantallas y permitió completar la atención. La administración de Personas y Asignaciones se probó en MySQL local, sin ejecutar sus escrituras sobre la base compartida durante esa validación.

Las tres herramientas de esta carpeta se verificaron con el protocolo autenticado, incluyendo preparación inválida, credenciales incorrectas, identidades distintas y salida sin sesiones residuales en los casos comprobados. Los resultados locales de una misma asignación y un único turno elegible siguen separados de los ensayos remotos. Las baterías amplias utilizaron auxiliares de prueba externos y no forman una suite Maven/CI reproducible íntegramente desde este repositorio. Los casos repetidos de regresión no se cuentan como escenarios nuevos.

La administración de servicios y ventanillas se comprobó con MySQL 8.0.32 local, JDK 26.0.2, destino Java 21 y JavaFX 13. Pasaron 21 comprobaciones de servidor y protocolo, diez de las vistas con respuestas controladas y 29 de regresión de Asignaciones. El cliente de catálogo también se probó por sockets contra el servidor y JDBC reales. Esas cifras corresponden a pruebas separadas de servidor e interfaz. El recorrido compartido posterior se describe a continuación.

Quedan pendientes el ensayo físico entre computadoras, una medición de rendimiento bajo carga y la revisión de fechas históricas anteriores al ajuste horario. Los resultados no acreditan carga prolongada ni ausencia de cualquier interbloqueo. Las herramientas de esta carpeta conectan a un servidor local y no validan por sí solas un despliegue TLS entre equipos.


### Recorrido del catálogo sobre la base compartida

El 13 de noviembre de 2026 se ejecutó sobre MySQL 8.0.32 compartido, con JDK 26.0.2, destino Java 21 y JavaFX 13. Los cuatro módulos compilaron. Servidor y clientes se ejecutaron en una sola computadora mediante loopback; MySQL era remoto.

| Comprobación | Resultado |
| --- | --- |
| Servicio desde la interfaz | Alta activa con consecutivo 0; edición de nombre y prefijo antes de emitir, confirmada en MySQL. |
| Ventanilla desde la interfaz | Alta activa sin habilitaciones ni asignación implícitas. |
| Cliente de administración | Baja y reactivación de ventanilla libre, cambios de modalidad, habilitaciones y apertura/cierre de asignación confirmados. |
| Validaciones | Diez rechazos correctos: duplicados de código, nombre, prefijo y ventanilla; prefijo inválido; bajas incompatibles, retirada con pendientes, cambio de prefijo tras emitir y cambio de modalidad con turno activo. |
| Generador y pantalla pública | Emisión regular y preferencial con el servicio nuevo, códigos y prioridad persistidos; llamados y finalizaciones visibles por la suscripción. |
| Atención mediante cliente real | La exclusiva tomó primero el preferencial y dejó en espera al regular. Al cambiar a mixta se atendió el regular. Ambos terminaron FINALIZADO con fechas en orden. |
| Cierre | Cuenta, servicio y ventanilla de QA inactivos, asignación cerrada y dos tickets finalizados. Se conservaron las mismas huellas de los 44 registros anteriores de las diez tablas. |

La autenticación de QA utilizó AuthServerClient con una cuenta exclusiva autorizada. Las altas y edición indicadas, las emisiones y la vista pública se comprobaron en las pantallas reales. Los cambios de modalidad, restricciones y atención utilizaron los clientes de protocolo reales; no se acredita que todos los botones administrativos se hayan recorrido en esta sesión. No se provocaron nuevos fallos de red ni conflictos concurrentes en la base compartida.

Los llamados con turno tardaron 6289 y 6154 ms de procesamiento. Cada uno abrió siete conexiones, cuyas aperturas sumaron aproximadamente 4,9 segundos. La petición sin turno tardó 6013 ms. Son observaciones del recorrido y no una prueba de carga.

Una comprobación visual separada, sin MySQL ni emisión, cargó 20 servicios de ejemplo en el generador. El último fue accesible y seleccionable; el botón de generar permaneció visible. Al desplazarse por la lista también se pierde de vista temporalmente el panel preferencial. El generador abierto no incorpora automáticamente altas posteriores: se debe volver a abrir para consultar el catálogo actualizado.
