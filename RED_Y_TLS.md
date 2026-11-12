# Configuración de red y TLS

Las cuatro aplicaciones leen `network.properties`. Sin archivo se conserva la ejecución local en 127.0.0.1:5000. Para configurar en NetBeans, copiar `src/main/resources/network.properties.example` como `network.properties` en cada proyecto y ajustar sus valores antes de compilar. El archivo privado está excluido de Git.

También se puede usar la propiedad Java `-Dturnos.config=RUTA_ABSOLUTA`: selecciona un archivo externo UTF-8 y tiene prioridad sobre el recurso del proyecto. Si se indica un archivo que no existe, el inicio falla explícitamente. Los almacenes relativos de ese archivo se resuelven respecto a su carpeta; para recursos incluidos en el proyecto usar rutas absolutas, evitando depender del directorio de ejecución de NetBeans.

| Propiedad | Uso |
| --- | --- |
| `server.host` | Dirección de CoreServer para los tres clientes. |
| `server.port` | Puerto común, entero entre 1 y 65535; predeterminado 5000. |
| `server.bind` | Dirección de escucha de CoreServer; predeterminada 127.0.0.1. |
| `tls.enabled` | `true` o `false`. Fuera del equipo se exige `true`. |
| `tls.keyStore` / `tls.keyStorePassword` | Archivo PKCS12 de clave privada y contraseña; solo CoreServer. |
| `tls.trustStore` / `tls.trustStorePassword` | Archivo PKCS12 de certificados confiables y contraseña; clientes. |

La configuración sin TLS se limita a direcciones loopback. Los clientes comprueban también la dirección realmente conectada antes de enviar el protocolo. CoreServer no abre una escucha en todas las interfaces sin TLS. No hay una opción para ignorar certificados ni un reintento automático con TCP si falla TLS.

## Preparar un despliegue entre equipos

1. Elegir el nombre o IP de la computadora que ejecutará CoreServer y un puerto disponible. Configurar `server.bind` con su dirección LAN; los clientes usan esa dirección en `server.host` y el mismo puerto. Mantener `tls.enabled=true` en todos.
2. Crear una clave/certificado del servidor en PKCS12. El certificado debe incluir el nombre/IP elegido en su extensión SAN. No alcanza con ponerlo solo en CN.
3. Distribuir únicamente el certificado público por un canal verificado. Importarlo en un almacén de confianza PKCS12 para cada cliente. La clave privada permanece en la computadora de CoreServer.
4. Completar las rutas y contraseñas privadas en los archivos locales. No versionar claves, almacenes o contraseñas. Los ejemplos contienen marcadores, no credenciales reales.
5. Compilar y ejecutar primero CoreServer, luego generador, pantalla pública y dos operadores. Comprobar el arranque, login, generación, llamado, rellamado y ciclo de atención, reportes, administración, dos suscriptores y reconexión.
6. Comprobar la conectividad de la red y sus reglas de acceso según el entorno del equipo. Este bloque no modifica el firewall ni instala certificados en Windows.

Ejemplo con `keytool` incluido en el JDK, sustituyendo NOMBRE_SERVIDOR e IP_SERVIDOR. Los comandos solicitan las contraseñas interactivamente:

```text
keytool -genkeypair -alias turnos -keyalg RSA -keysize 3072 -validity 365 -storetype PKCS12 -keystore servidor.p12 -dname "CN=NOMBRE_SERVIDOR" -ext "SAN=dns:NOMBRE_SERVIDOR,ip:IP_SERVIDOR"
keytool -exportcert -alias turnos -keystore servidor.p12 -rfc -file servidor.crt
keytool -importcert -alias turnos -file servidor.crt -storetype PKCS12 -keystore confianza.p12
```

Verificar el certificado antes de confirmar su importación. Para un despliegue con una autoridad certificadora existente, utilizar su certificado/cadena correspondiente en lugar del ejemplo autofirmado.

## Comportamiento y límites

`NetworkSettings` centraliza lectura, validación, sockets y contexto TLS dentro de cada aplicación. Los cuatro proyectos conservan su independencia Maven: contienen copias equivalentes del transporte con distinto paquete. Al modificarlo hay que mantener las cuatro implementaciones sincronizadas.

Los clientes de acceso, atención, reportes, personas, asignaciones, generación y pantalla pública utilizan este transporte, incluido el canal SUBSCRIBE_DISPLAY. Conservan sus plazos de conexión/lectura y la lógica de reconexión existente. El handshake TLS se completa y verifica antes de enviar credenciales o comandos. El nombre del certificado se valida mediante la identificación de extremo HTTPS de JSSE.

Los constructores anteriores que reciben host/puerto se conservan; leen el modo TLS de la configuración. El constructor anterior de TurnServer que recibe solo puerto conserva la escucha local sin TLS para las herramientas de prueba. La aplicación CoreServer utiliza el constructor con NetworkSettings y, por tanto, respeta la configuración elegida.

La configuración se lee al arrancar para elegir host/puerto y al abrir conexiones para preparar el transporte. Reiniciar las aplicaciones después de cambiarla; no editar archivos mientras hay sesiones o suscripciones abiertas.

Esto protege el enlace entre aplicaciones y CoreServer. La configuración JDBC y la conexión de CoreServer a MySQL se administran por separado mediante `db.properties`; no se cambió su política TLS en este bloque.

La validación realizada es local. El ensayo físico entre computadoras y su medición de rendimiento siguen pendientes. Ver [las pruebas del transporte](pruebas/QA_red_2026-11-12.md).
