param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$alumnos = @(
    @{ firstName = "Andrea";    lastName = "Quispe Flores";       birthDate = "2005-03-12" },
    @{ firstName = "Luis";      lastName = "Ramirez Soto";        birthDate = "2004-07-25" },
    @{ firstName = "Valeria";   lastName = "Mendoza Torres";      birthDate = "2006-01-18" },
    @{ firstName = "Carlos";    lastName = "Huaman Rojas";        birthDate = "2003-11-09" },
    @{ firstName = "Camila";    lastName = "Vargas Castillo";     birthDate = "2005-05-30" },
    @{ firstName = "Diego";     lastName = "Paredes Silva";       birthDate = "2004-09-14" },
    @{ firstName = "Luciana";   lastName = "Chavez Medina";       birthDate = "2006-02-22" },
    @{ firstName = "Miguel";    lastName = "Salazar Cruz";        birthDate = "2003-08-17" },
    @{ firstName = "Daniela";   lastName = "Ramos Vega";          birthDate = "2005-12-03" },
    @{ firstName = "Jorge";     lastName = "Navarro Pena";        birthDate = "2004-04-11" },
    @{ firstName = "Mariana";   lastName = "Caceres Luna";        birthDate = "2006-06-26" },
    @{ firstName = "Fernando";  lastName = "Ortega Campos";       birthDate = "2003-10-05" },
    @{ firstName = "Sofia";     lastName = "Reyes Aguilar";       birthDate = "2005-08-19" },
    @{ firstName = "Alejandro"; lastName = "Espinoza Castro";     birthDate = "2004-02-28" },
    @{ firstName = "Natalia";   lastName = "Morales Herrera";     birthDate = "2006-09-07" },
    @{ firstName = "Sebastian"; lastName = "Gutierrez Valdez";    birthDate = "2003-05-21" },
    @{ firstName = "Paula";     lastName = "Lopez Miranda";       birthDate = "2005-01-16" },
    @{ firstName = "Ricardo";   lastName = "Alvarez Benites";     birthDate = "2004-12-10" },
    @{ firstName = "Gabriela";  lastName = "Fernandez Acosta";    birthDate = "2006-03-24" },
    @{ firstName = "Mateo";     lastName = "Cordova Palomino";    birthDate = "2003-07-13" },
    @{ firstName = "Ximena";    lastName = "Villanueva Arias";    birthDate = "2005-10-29" },
    @{ firstName = "Renato";    lastName = "Sanchez Cabrera";     birthDate = "2004-06-08" },
    @{ firstName = "Alessandra";lastName = "Rios Delgado";        birthDate = "2006-04-15" },
    @{ firstName = "Martin";    lastName = "Mejia Contreras";     birthDate = "2003-09-27" },
    @{ firstName = "Fiorella";  lastName = "Valencia Nunez";      birthDate = "2005-02-06" },
    @{ firstName = "Adrian";    lastName = "Zamora Espino";       birthDate = "2004-10-20" },
    @{ firstName = "Claudia";   lastName = "Mamani Calderon";     birthDate = "2006-07-04" },
    @{ firstName = "Bruno";     lastName = "Carrillo Fuentes";    birthDate = "2003-03-31" },
    @{ firstName = "Elena";     lastName = "Tapia Bustamante";    birthDate = "2005-11-23" },
    @{ firstName = "Nicolas";   lastName = "Guzman Peralta";      birthDate = "2004-01-09" }
)

Write-Host "Comprobando conexión con $BaseUrl..." -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/students" -Method Get | Out-Null
} catch {
    Write-Host "No se pudo conectar con el sistema en $BaseUrl." -ForegroundColor Red
    Write-Host "Primero inicia Spring Boot y vuelve a ejecutar este script." -ForegroundColor Yellow
    exit 1
}

$registrados = 0
$fallidos = 0

for ($indice = 0; $indice -lt $alumnos.Count; $indice++) {
    $numero = $indice + 1
    $alumno = $alumnos[$indice]

    $payload = @{
        firstName       = $alumno.firstName
        lastName        = $alumno.lastName
        enrollmentCode  = "U{0:D8}" -f $numero
        documentNumber  = "{0:D8}" -f (70000000 + $numero)
        email           = "alumno{0:D2}@universidad.edu.pe" -f $numero
        phone           = "{0:D9}" -f (900000000 + $numero)
        birthDate       = $alumno.birthDate
    }

    try {
        Invoke-RestMethod `
            -Uri "$BaseUrl/api/students" `
            -Method Post `
            -ContentType "application/json; charset=utf-8" `
            -Body ($payload | ConvertTo-Json) | Out-Null

        $registrados++
        Write-Host ("[OK {0:D2}/30] {1} {2} - {3}" -f $numero, $alumno.firstName, $alumno.lastName, $payload.enrollmentCode) -ForegroundColor Green
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

        Write-Host ("[ERROR {0:D2}/30] {1} {2}: {3}" -f $numero, $alumno.firstName, $alumno.lastName, $mensaje) -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Proceso terminado" -ForegroundColor Cyan
Write-Host "Registrados: $registrados" -ForegroundColor Green
Write-Host "Fallidos:    $fallidos" -ForegroundColor $(if ($fallidos -eq 0) { "Green" } else { "Red" })
Write-Host ""
Write-Host "Credenciales: cada alumno ingresa usando su código como usuario y contraseña." -ForegroundColor Yellow
Write-Host "Ejemplo: U00000001 / U00000001"
