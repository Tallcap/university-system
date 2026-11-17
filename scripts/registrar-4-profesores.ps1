param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

# Los escapes \u permiten conservar las tildes incluso usando Windows PowerShell 5.
$profesores = @(
    @{
        firstName = "Mariana"; lastName = "Torres Vega"; teacherCode = "C00000002"
        documentNumber = "72000001"; email = "mariana.torres@universidad.edu.pe"
        phone = "910000001"; hireDate = "2022-03-01"
        academicDegreeJson = "Mag\u00edster"; specialtyJson = "Matem\u00e1tica"
    },
    @{
        firstName = "Patricia"; lastName = "Salazar Rojas"; teacherCode = "C00000003"
        documentNumber = "72000002"; email = "patricia.salazar@universidad.edu.pe"
        phone = "910000002"; hireDate = "2021-08-15"
        academicDegreeJson = "Titulado"; specialtyJson = "Enfermer\u00eda"
    },
    @{
        firstName = "Daniel"; lastName = "Mendoza Castro"; teacherCode = "C00000004"
        documentNumber = "72000003"; email = "daniel.mendoza@universidad.edu.pe"
        phone = "910000003"; hireDate = "2023-01-10"
        academicDegreeJson = "Mag\u00edster"; specialtyJson = "Redacci\u00f3n de Textos"
    },
    @{
        firstName = "Claudia"; lastName = "Reyes Campos"; teacherCode = "C00000005"
        documentNumber = "72000004"; email = "claudia.reyes@universidad.edu.pe"
        phone = "910000004"; hireDate = "2022-07-20"
        academicDegreeJson = "Titulado"; specialtyJson = "Ingl\u00e9s"
    }
)

Write-Host "Comprobando conexión con $BaseUrl..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/api/teachers" -UseBasicParsing
    $existentes = $response.Content | ConvertFrom-Json
} catch {
    Write-Host "No se pudo conectar con el sistema en $BaseUrl." -ForegroundColor Red
    Write-Host "Primero inicia Spring Boot y vuelve a ejecutar este script." -ForegroundColor Yellow
    exit 1
}

$registrados = 0
$omitidos = 0
$fallidos = 0

foreach ($profesor in $profesores) {
    $yaExiste = $existentes | Where-Object {
        $_.teacherCode -eq $profesor.teacherCode
    } | Select-Object -First 1

    if ($yaExiste) {
        $omitidos++
        Write-Host "[OMITIDO] $($profesor.teacherCode) ya está registrado." -ForegroundColor Yellow
        continue
    }

    $json = @"
{
  "firstName": "$($profesor.firstName)",
  "lastName": "$($profesor.lastName)",
  "teacherCode": "$($profesor.teacherCode)",
  "documentNumber": "$($profesor.documentNumber)",
  "email": "$($profesor.email)",
  "phone": "$($profesor.phone)",
  "hireDate": "$($profesor.hireDate)",
  "academicDegree": "$($profesor.academicDegreeJson)",
  "specialty": "$($profesor.specialtyJson)"
}
"@

    try {
        Invoke-RestMethod `
            -Uri "$BaseUrl/api/teachers" `
            -Method Post `
            -ContentType "application/json; charset=utf-8" `
            -Body ([Text.Encoding]::UTF8.GetBytes($json)) | Out-Null

        $registrados++
        Write-Host "[OK] $($profesor.teacherCode) - $($profesor.firstName) $($profesor.lastName)" -ForegroundColor Green
    } catch {
        $fallidos++
        $mensaje = $_.Exception.Message

        if ($_.ErrorDetails.Message) {
            try {
                $detalle = $_.ErrorDetails.Message | ConvertFrom-Json
                if ($detalle.message) {
                    $mensaje = $detalle.message
                } elseif ($detalle.detail) {
                    $mensaje = $detalle.detail
                }
            } catch {
                # Conserva el mensaje original si la respuesta no es JSON.
            }
        }

        Write-Host "[ERROR] $($profesor.teacherCode): $mensaje" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Proceso terminado" -ForegroundColor Cyan
Write-Host "Registrados: $registrados" -ForegroundColor Green
Write-Host "Omitidos:    $omitidos" -ForegroundColor Yellow
Write-Host "Fallidos:    $fallidos" -ForegroundColor $(if ($fallidos -eq 0) { "Green" } else { "Red" })
Write-Host ""
Write-Host "Cada docente usa su código como usuario y contraseña." -ForegroundColor Yellow
Write-Host "Ejemplo: C00000002 / C00000002"
