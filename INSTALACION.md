# Instalación desde cero

Esta guía prepara una base nueva y ejecuta los cuatro proyectos en NetBeans. Para actualizar una base que ya contiene datos, revisar las migraciones correspondientes; no volver a ejecutar el esquema ni los datos iniciales sobre ella.

## 1. Preparar las herramientas

- JDK 21 o superior, seleccionado también en NetBeans.
- NetBeans con soporte Java/Maven. Abrir las carpetas `CoreServer`, `CostumerTurn`, `OperatorSystem` y `QueueDisplayScreen` como proyectos separados.
- MySQL 8 y una cuenta con permisos sobre la base elegida. La instalación se comprobó con MySQL 8.0.32.
- Acceso a Internet para que Maven descargue las dependencias que todavía no estén en su caché. Los clientes usan JavaFX 13 mediante Maven; no necesitan una instalación independiente de JavaFX para **Run Project**.

## 2. Crear la base e importar los datos

Crear una base vacía, por ejemplo `gestion_turnos`, y seleccionarla en el cliente MySQL:

```sql
CREATE DATABASE gestion_turnos
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE gestion_turnos;
```

Ejecutar, en este orden y dentro de esa base:

1. [database/script borrador.sql](database/script%20borrador.sql).
2. [database/datos_prueba.sql](database/datos_prueba.sql).

El esquema actual ya incorpora las migraciones de acceso, pausa y rellamado. No aplicarlas de nuevo en esta instalación.

El resultado inicial debe ser: **10 tablas, 5 funcionarios, 3 servicios, 4 ventanillas y 3 asignaciones abiertas**. No hay turnos ni credenciales predeterminadas. Los consecutivos de INFO, TRAM y PAGO comienzan en cero.

Los datos son de demostración. Incluyen dos ventanillas mixtas, una exclusiva para preferenciales y una inactiva. La compatibilidad de servicios depende tanto del funcionario como de la ventanilla.

## 3. Configurar MySQL en CoreServer

Crear `CoreServer/src/main/resources/db.properties` como archivo UTF-8:

```properties
db.url=jdbc:mysql://HOST:3306/gestion_turnos?connectionTimeZone=SERVER&preserveInstants=true
db.usuario=USUARIO_MYSQL
db.clave=CONTRASEÑA_MYSQL
```

Sustituir host, puerto, base y credenciales por los del entorno propio. Conservar los parámetros horarios: los campos `TIMESTAMP` deben convertirse correctamente entre MySQL y Java. Solo CoreServer necesita este archivo; los tres clientes acceden a sus datos mediante el servidor.

El archivo está excluido de Git. Las contraseñas de MySQL y de los funcionarios son credenciales diferentes.

## 4. Preparar el primer acceso administrativo

1. Compilar CoreServer para que se copie su configuración a los recursos de ejecución.
2. Abrir `cr.ac.una.config.CredentialSetup` y ejecutar **Run File**, siguiendo [INICIALIZAR_ACCESO.md](database/INICIALIZAR_ACCESO.md).
3. Indicar `admin`, el usuario administrativo que ya existe en los datos iniciales. Definir y confirmar una contraseña propia de 8 a 128 caracteres.
4. Preparar también `amora` y `lvargas` con la misma herramienta, usando una contraseña elegida para cada cuenta. Como alternativa, después del primer ingreso administrativo se pueden gestionar las credenciales desde **Personas**.

No insertar contraseñas en SQL ni escribirlas como argumentos de ejecución. Si NetBeans no ofrece una consola interactiva, la herramienta advierte que la entrada será visible. No grabar ni compartir ese paso.

La herramienta también restablece contraseñas existentes. No es necesario ejecutarla en cada arranque.

## 5. Ejecutar las aplicaciones

Para probar todo en una sola computadora, no hace falta crear `network.properties`: las aplicaciones utilizan `127.0.0.1:5000`. Si se cambia el puerto, debe coincidir en los cuatro proyectos. Para conectar otras computadoras, preparar primero [la configuración y los certificados TLS](RED_Y_TLS.md).

1. Ejecutar CoreServer con `cr.ac.una.CoreServer.App` y mantenerlo abierto.
2. Ejecutar CostumerTurn con **Run Project**. Debe cargar Información general, Trámites y Pagos.
3. Ejecutar QueueDisplayScreen con **Run Project**. Debe indicar que está conectado; inicialmente las listas estarán vacías.
4. Ejecutar OperatorSystem con **Run Project** e ingresar como `amora`. Debe mostrar su asignación a la ventanilla 1.
5. Para comprobar dos funcionarios, abrir otra ejecución de OperatorSystem e ingresar como `lvargas`, asignado a la ventanilla 2.

Si se modifica `db.properties` o la configuración de red, recompilar el proyecto correspondiente y reiniciar las aplicaciones afectadas. Las sesiones se pierden al reiniciar CoreServer.

## 6. Comprobar el recorrido inicial

- Generar un turno regular y uno preferencial de Información general, y uno regular de Trámites. En una base nueva deben aparecer A001, A002 y B001.
- Con ambas asignaciones libres, llamar primero desde la ventanilla 1: debe tomar A002 preferencial. Después llamar desde la 2: debe tomar A001 regular.
- En el turno llamado, usar **Volver a llamar**. Debe conservar el código y el primer llamado, aumentar su contador y actualizar la pantalla pública.
- Usar **Iniciar atención** y después **Finalizar atención**. El historial debe conservarse y la asignación debe quedar libre.
- Ingresar como `admin` para acceder a **Personas**, **Asignaciones** y las cinco consultas. Seleccionar un rango de fechas que incluya las atenciones realizadas. El administrador inicial no tiene asignación de atención.

Si el catálogo no carga, revisar la consola de CoreServer y su conexión a MySQL antes de usar **Reconectar**. Si falta acceso, comprobar que el funcionario esté activo y tenga una credencial creada. No repetir automáticamente una operación cuando se pierde su confirmación: consultar primero el estado guardado.

## Verificación y alcance

La instalación se comprobó desde una copia limpia de los archivos versionados, con MySQL local, comunicación TLS y clientes en procesos separados. El [resumen de comprobaciones](pruebas/README.md#comprobaciones-y-límites) precisa el entorno y sus límites. El ensayo físico entre computadoras continúa pendiente.
