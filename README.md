# Club Accounting API

Spring Boot REST API for project login, accounts, and bookings. The API contract is in `documentation/openapi.yaml`; its Java interfaces and models are generated during the Gradle build.

## Local setup

Use Java 17 or newer. Generate a local self-signed certificate for HTTPS:

```powershell
New-Item -ItemType Directory -Force src/main/resources/keystore
keytool -genkeypair -alias accounting-local -keyalg RSA -keysize 2048 -validity 3650 `
  -storetype PKCS12 -keystore src/main/resources/keystore/keystore.p12 `
  -storepass changeit -keypass changeit `
  -dname "CN=localhost, OU=Development, O=BZZ, L=Bern, ST=Bern, C=CH" `
  -ext "SAN=dns:localhost,ip:127.0.0.1" -noprompt
```

Set a private JWT signing key and start the application:

```powershell
$env:JWT_SECRET = (python -c "import secrets; print(secrets.token_urlsafe(48))")
.\gradlew.bat bootRun
```

The local API is available at `https://localhost:8443`; the HTTP connector listens on port 8080. The certificate is self-signed, so local clients must trust it explicitly. By default, data is stored in the ignored `data/` directory using H2. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to use another database.

## Tests

```powershell
.\gradlew.bat test
```
