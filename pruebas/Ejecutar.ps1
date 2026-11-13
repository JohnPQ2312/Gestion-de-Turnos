# Compila las herramientas en .build y ejecuta la prueba elegida con el puerto indicado.
# Las pruebas llaman turnos reales; requieren preparar las asignaciones como indica pruebas/README.md.
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('ConcurrentCallingCheck', 'SameAssignmentCallingCheck', 'SingleEligibleTicketCheck')]
    [string]$Prueba,
    [int[]]$Parametros = @(),
    [ValidateRange(1, 65535)]
    [int]$Puerto = 5000
)

$ErrorActionPreference = 'Stop'
$salidaClases = Join-Path $PSScriptRoot '.build'
New-Item -ItemType Directory -Path $salidaClases -Force | Out-Null
$fuentes = @('TestAccess.java', 'ConcurrentCallingCheck.java',
    'SameAssignmentCallingCheck.java', 'SingleEligibleTicketCheck.java') |
    ForEach-Object { Join-Path $PSScriptRoot $_ }

# Compila juntas las herramientas y el acceso compartido; un fallo detiene la ejecución.
& javac -encoding UTF-8 --release 21 -d $salidaClases @fuentes
if ($LASTEXITCODE -ne 0) {
    throw 'No se pudieron compilar las herramientas. Se necesita JDK 21 o superior.'
}

# Conserva los parámetros de la prueba y devuelve al terminar su mismo código de salida.
$argumentosPrueba = @($Parametros | ForEach-Object { $_.ToString() })
& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' "-Dturnos.pruebas.puerto=$Puerto" `
    -cp $salidaClases $Prueba @argumentosPrueba
exit $LASTEXITCODE
