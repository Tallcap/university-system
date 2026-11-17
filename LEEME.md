# University System (Java 21 + Spring Boot + SQL Server)

Sistema universitario desarrollado con Java, Spring Boot, Maven y SQL Server. Las dependencias se encuentran en `pom.xml` y Maven las descarga automáticamente; no se utiliza `pip install`.

## 1. Requisitos

- JDK 21 o superior (`java -version`).
- SQL Server Express o Developer.
- TCP/IP habilitado en el puerto `1433` desde SQL Server Configuration Manager.
- Autenticación mixta habilitada y un usuario de SQL Server, por ejemplo `sa`.
- Conexión a Internet la primera vez que Maven descargue las dependencias.

No es necesario instalar Maven globalmente: el proyecto incluye `mvnw` y `mvnw.cmd`.

## 2. Base de datos

El repositorio incluye los scripts SQL necesarios dentro de `db/`:

1. `01_create_database.sql`: crea la base `UniversitySystem`.
2. `02_schema.sql`: crea manualmente el esquema base.
3. `03_add_class_participation.sql` a `06_add_academic_task_type.sql`: agregan las modificaciones posteriores.

La forma recomendada es ejecutar solamente `01_create_database.sql` y luego iniciar la aplicación. Hibernate crea o actualiza las tablas mediante `spring.jpa.hibernate.ddl-auto=update`.

En SSMS o Azure Data Studio, abre y ejecuta:

```text
db/01_create_database.sql
```

También puede ejecutarse con `sqlcmd`:

```powershell
sqlcmd -S localhost -U sa -P TU_CLAVE -i db/01_create_database.sql
```

Si se desea crear todo manualmente, ejecuta los scripts `01` a `06` en orden.

> Los scripts incluidos crean la base y su estructura, pero no contienen los registros actuales de alumnos, docentes, cursos, notas o recomendaciones de IA. La carpeta `scripts/` contiene datos y automatizaciones de demostración.

## 3. Configuración segura

La aplicación utiliza variables de entorno para no guardar contraseñas ni claves privadas en GitHub:

- `DB_HOST`: servidor SQL; por defecto `localhost`.
- `DB_PORT`: puerto SQL; por defecto `1433`.
- `DB_NAME`: base de datos; por defecto `UniversitySystem`.
- `DB_USER`: usuario SQL; por defecto `sa`.
- `DB_PASSWORD`: contraseña de SQL Server; obligatoria.
- `GEMINI_API_KEY`: clave de Gemini; necesaria para generar recomendaciones.
- `APIINTI_TOKEN`: token para consultar DNI mediante API Inti.

Ejemplo en PowerShell:

```powershell
$env:DB_PASSWORD="TU_CLAVE_SQL_SERVER"
$env:GEMINI_API_KEY="TU_CLAVE_GEMINI"
$env:APIINTI_TOKEN="TU_TOKEN_API_INTI"
```

Estas variables existen solamente en la ventana actual de PowerShell. Si se cierra, deben configurarse nuevamente.

No publiques claves reales en `application.properties`, archivos `.env`, capturas o commits.

## 4. Ejecutar en Windows

Desde la carpeta raíz del proyecto:

```powershell
$env:DB_PASSWORD="TU_CLAVE_SQL_SERVER"
$env:GEMINI_API_KEY="TU_CLAVE_GEMINI"
$env:APIINTI_TOKEN="TU_TOKEN_API_INTI"
.\mvnw.cmd spring-boot:run
```

Cuando aparezca `Started UniversitySystemApplication`, abre:

- Aplicación: <http://localhost:8080/>
- Inicio de sesión: <http://localhost:8080/login.html>

Para detener la aplicación, presiona `Ctrl + C` en la misma terminal.

## 5. Ejecutar en Linux o macOS

```bash
export DB_PASSWORD="TU_CLAVE_SQL_SERVER"
export GEMINI_API_KEY="TU_CLAVE_GEMINI"
export APIINTI_TOKEN="TU_TOKEN_API_INTI"
./mvnw spring-boot:run
```

## 6. Usuario de demostración

La aplicación crea automáticamente el administrador de demostración:

```text
Usuario: j4v
Contraseña: admin123
```

Estas credenciales son únicamente para desarrollo o demostración y deben cambiarse antes de publicar el sistema en un servidor real.

## 7. Gemini

La configuración de IA utiliza `gemini-3.1-flash-lite` como modelo recomendado. Cuando un modelo presenta un error temporal de servicio, el backend intenta utilizar el modelo de respaldo configurado.

Sin `GEMINI_API_KEY`, el resto del sistema continúa funcionando, pero las solicitudes de recomendación de IA quedarán en estado `FAILED`.

## 8. API Inti

La consulta por DNI carga nombres y apellidos para el registro de alumnos y docentes. Para utilizarla se necesita un token válido configurado mediante `APIINTI_TOKEN` o mediante la configuración local correspondiente.

## 9. Pruebas

Para compilar y ejecutar las pruebas automáticas:

```powershell
.\mvnw.cmd test
```

## 10. Errores frecuentes

- `Cannot open database UniversitySystem`: falta ejecutar `db/01_create_database.sql`.
- `Login failed for user 'sa'`: `DB_PASSWORD` es incorrecta, `sa` está deshabilitado o falta activar la autenticación mixta.
- `Connection refused` o `timeout`: SQL Server no está iniciado, TCP/IP no está habilitado o el puerto `1433` no está disponible.
- `Port 8080 was already in use`: otra instancia está ejecutándose en el puerto `8080`; detén ese proceso antes de iniciar nuevamente.
- `invalid target release: 21`: `JAVA_HOME` no apunta a un JDK compatible.
- Gemini responde `401` o `403`: revisa la clave y los permisos del proyecto.
- Gemini responde `429`: se alcanzó la cuota o el límite de solicitudes.
- Gemini responde `503`: el modelo está temporalmente saturado; vuelve a intentarlo más tarde.

## 11. Subir cambios a GitHub

```powershell
git add .
git commit -m "Descripción del cambio"
git push
```

Antes de cada `push`, revisa `git status` y confirma que no se estén incluyendo contraseñas, claves API, respaldos con datos personales, `target/` ni archivos `.env`.
