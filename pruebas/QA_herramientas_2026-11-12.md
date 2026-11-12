# Corrección de las herramientas de concurrencia

Fecha: 12 de noviembre de 2026. Seguimiento de la [revisión integral](QA_2026-11-12.md).

## Problema y corrección

Las tres herramientas enviaban LIST_ASSIGNMENTS, CALL_NEXT y GET_ACTIVE_TICKET sin sesión. Después de incorporar autenticación al servidor, esos comandos recibían DENIED. Además, la lista ahora contiene únicamente asignaciones del funcionario autenticado: una sola cuenta no puede preparar las dos ventanillas.

Se corrigieron ConcurrentCallingCheck, SameAssignmentCallingCheck y SingleEligibleTicketCheck para usar una sesión propia por funcionario. TestAccess centraliza el acceso y el cierre para no repetirlos en las tres herramientas. Comprueba que la cuenta ingresada corresponda al funcionario solicitado y vincula cada asignación a su token antes del envío concurrente.

Se mantiene la ausencia de reintentos de escritura. Las sesiones se cierran al terminar, incluso si falla la preparación; si el servidor no confirma LOGOUT, se informa. Las contraseñas se solicitan al ejecutar y no se pasan como argumentos ni se guardan en archivos. La entrada se oculta cuando Java dispone de consola interactiva; en caso contrario se advierte que será visible.

Las herramientas devuelven código 0 al aprobar y 1 ante fallos o preparación inválida. Antes podían mostrar una prueba detenida y terminar con código de éxito. Ejecutar.ps1 compila las cuatro clases para Java 21 y conserva ese resultado, de modo que no es necesario compilar manualmente TestAccess.

## Validación

**15 casos aprobados y 0 fallidos**, usando los archivos finales del repositorio, MySQL 8.0.32 local y el servidor TCP de producción. Son adicionales a los 60 casos de la revisión integral; no se volvió a sumar esa batería por repetir ejecuciones.

Se comprobaron las tres pruebas normales y también credenciales incorrectas, identidad distinta, asignación inexistente, cola insuficiente, ventanilla ocupada, parámetros inválidos y fin de entrada. En cada caso se revisaron los datos y el registro de sesiones del servidor: sin llamadas parciales por fallos de preparación y sin sesiones residuales en las ejecuciones comprobadas.

Para verificar que las herramientas detectan defectos, un intermediario local alteró la identidad del operador en una confirmación de llamado. El llamado real se conservó en MySQL; cada herramienta detectó la inconsistencia y terminó con resultado fallido, sin reintentar el llamado. También se ejecutó el lanzador PowerShell con un caso correcto y otro rechazado.

No se usó ni se modificó la base compartida. No se realizaron cambios en los flujos funcionales de las cuatro aplicaciones en este seguimiento.

## Archivos y evidencias

- Tres herramientas: pruebas concurrentes autenticadas, preparación por cuenta y códigos de salida.
- TestAccess.java: sesiones y asociación de asignaciones, sin credenciales MySQL.
- Ejecutar.ps1: compilación y ejecución con JDK 21 o superior.
- README principal, guía de acceso y README de pruebas: instrucciones actualizadas.
- [Resultado de los 15 casos](evidencias/qa_2026-11-12/herramientas-autenticadas.log).
- [Matriz de casos](evidencias/qa_2026-11-12/matriz-herramientas.csv).

La [guía de ejecución](README.md) conserva las evidencias históricas y explica la preparación de los datos y las cuentas. Los .class generados están excluidos de Git. Estas herramientas prueban el servidor que se tenga abierto: antes de usarlas con una base de equipo hay que coordinar sus datos, porque CALL_NEXT modifica turnos reales.

## Lo que sigue

Las utilidades de concurrencia ya no están pendientes de adaptación. Se mantiene por separado el trabajo de integrar las regresiones amplias en una ejecución automática, medir rendimiento remoto y completar rellamado, administración, configuración de red y documentación de entrega. Los prefijos y las reglas de modificación del historial deben concretarse al implementar la administración; no se cambió esa política durante este seguimiento.
