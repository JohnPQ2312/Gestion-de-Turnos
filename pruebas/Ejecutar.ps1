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

& javac -encoding UTF-8 --release 21 -d $salidaClases @fuentes
if ($LASTEXITCODE -ne 0) {
    throw 'No se pudieron compilar las herramientas. Se necesita JDK 21 o superior.'
}

$argumentosPrueba = @($Parametros | ForEach-Object { $_.ToString() })
& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' "-Dturnos.pruebas.puerto=$Puerto" `
    -cp $salidaClases $Prueba @argumentosPrueba
exit $LASTEXITCODE
