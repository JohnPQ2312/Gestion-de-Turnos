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

La validación local incluyó los repositorios JDBC, el servidor, los clientes y las vistas JavaFX sobre MySQL 8.0.32 desechable, con JDK 25.0.3 y 26.0.2, destino de compilación Java 21 y JavaFX 13. No se probó por separado un runtime JDK 21.

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

Se comprobó el código actual con MySQL 8.0.32 local desechable, JDK 26.0.2, destino Java 21 y JavaFX 13. Un escenario integrado de 64 comprobaciones inició el servidor y las cuatro aplicaciones reales y las condujo por sus controles en ventanas fuera de pantalla. Incluyó cinco turnos, dos operadores, dos llamados simultáneos, un rellamado, las cinco consultas y el corte del servidor. Otras 21 comprobaciones cubren el anuncio de la pantalla pública y 15 la pérdida de la base de datos con el sistema en marcha: el servidor siguió activo, el generador y el operador mostraron el mensaje de error y el mismo servidor recuperó el servicio cuando MySQL volvió. Todo se ejecutó en una sola computadora por loopback, sin TLS y con ayudas de prueba externas a este repositorio. Una copia de los archivos que se entregan compiló con Maven sin conexión en los cuatro proyectos.

Con versiones anteriores del código se comprobó la instalación desde una copia limpia de los archivos versionados: se aplicaron esquema y datos iniciales, se prepararon credenciales con `CredentialSetup` y se ejecutó el recorrido por TLS, también con clientes en procesos separados en la misma computadora. La compilación usó dependencias de la caché de Maven y no verificó su primera descarga en un equipo sin caché.

### Base asignada por el docente

Sobre la base asignada (MySQL 8.4.7) se comprobaron versiones anteriores del código: la generación con persistencia y la continuación de consecutivos, el ciclo de atención, dos llamados coordinados a asignaciones distintas (dos turnos distintos y ninguna ventanilla con más de un turno activo) y el rellamado después de aplicar las migraciones. Antes de los últimos cambios se recorrió también el catálogo, con los cuatro módulos compilados y todo ejecutándose en una computadora por loopback contra la base remota. Ese recorrido incluyó el alta y la edición de un servicio, el alta de una ventanilla, las habilitaciones, la asignación, dos emisiones, diez rechazos esperados y la atención completa de dos turnos. Los registros previos se conservaron. Quedaron en la base una cuenta ADMIN, un servicio y una ventanilla de QA inactivos, una asignación cerrada y dos turnos finalizados. Las altas, la edición, las emisiones y la vista pública se comprobaron en las pantallas reales; los cambios de modalidad, las restricciones y la atención usaron clientes de protocolo reales.

Con el código actual se ejecutaron además las aplicaciones contra la base asignada: se emitió un turno de Información general, se llamó y rellamó un turno de Trámites desde la asignación 1 (`operador1`, ventanilla 1) y se inició y finalizó su atención. La base conserva esas operaciones con sus fechas. Esa sesión cubrió el ciclo de atención de un operador.

Dos llamados con turno tardaron cerca de seis segundos de procesamiento porque cada uno abrió siete conexiones. Es una observación de ese recorrido; el código actual reduce las conexiones por llamado.

### Herramientas de esta carpeta

Las tres herramientas se verificaron con el protocolo autenticado, incluidas la preparación inválida, las credenciales incorrectas y las identidades distintas, y cierran sus sesiones al terminar. Sus resultados corresponden a un servidor local. Las baterías amplias usaron auxiliares externos que no forman parte de este repositorio, así que no hay una suite Maven/CI reproducible desde él.

### Alcance y trabajo futuro

Las herramientas y los escenarios se conectan a un servidor local. Como trabajo futuro quedan el despliegue entre computadoras con TLS, una medición de rendimiento bajo carga y la revisión de fechas históricas anteriores al ajuste horario.
