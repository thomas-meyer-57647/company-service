# AGENTS.md

## Ziel
Pflichtenheft unter /doc vollständig umsetzen. Am Ende muss gelten:

- **Build + Tests grün**
- **Tests laufen isoliert nur mit H2** (und optional zusätzlich mit Testcontainers)
- **Dockerfile + docker-compose vorhanden** (Runtime optional, nicht Voraussetzung)
- **Swagger/OpenAPI vollständig (Controller/DTOs/Enums)**

## Build & Test (MUSS grün sein)
- `mvnw.cmd -q clean test`
- optional: `mvnw.cmd -q clean verify`

## Arbeitsweise (agent-stabil)
- Kleine Schritte: **max. 3–5 Dateien pro Änderung**
- Max. **120 Zeilen Output pro Antwort**
- Keine langen Analysen, nur: **Kurzplan + Dateien + Patch/Diff + Tests**
- Wenn Infos fehlen: **STOP** und exakt benötigte Datei nennen (Pfad)
- Keine Bestätigungsfragen/„OK“-Klicks – **direkt umsetzen**

## Technologie (MUSS)
- Java 21, Spring Boot 4.x, Maven, Flyway, **Spring Data JPA (Hibernate)**, REST/JSON
- Konfiguration via `application.properties`
- `server.servlet.context-path=/api/v1`
- `server.port` default `8108`

### WICHTIG: Kein JDBC verwenden
- **Kein spring-jdbc, kein JdbcTemplate, kein direktes JDBC**
- Persistenz ausschließlich über **JPA/Hibernate** (Repositories/EntityManager)

## Persistence (MUSS)
- Schema ausschließlich über **Flyway** (Source of Truth)
- `spring.jpa.hibernate.ddl-auto=none`
- Transaktionen via `@Transactional` in Services

## Tests (MUSS) – positiv/negativ + H2 + optional Testcontainers
- Jede Funktion: mind. **1 positiver + 1 negativer Test**
- Web/Security: **MockMvc + spring-security-test**

### Test-Datenbank
- **Standard (Default, ohne Docker): Embedded H2** (läuft immer, keine externen Services)
- **Optional (wenn Docker verfügbar): H2 als Container via Testcontainers**
  - Container nur verwenden, wenn Docker wirklich verfügbar ist (kein harter Zwang)
  - Umschaltbar z. B. per Profile/Property (z. B. `-Dtest.db=tc`)

### WICHTIG: Keine extern laufenden Services für Tests
- **Tests müssen vollständig isoliert laufen.**
- **Es dürfen keine lokal gestarteten Services vorausgesetzt werden** (keine MariaDB, kein docker-compose für Tests).
- **Andere Services laufen nicht.** Nur H2 (embedded oder optional Container).
- Flyway läuft in Tests gegen H2; falls nötig: `db/migration-h2` + `spring.flyway.locations` erweitern

## Swagger/OpenAPI (MUSS, ausführlich)
- Controller: `@Tag`, `@Operation`, `@ApiResponses` (400/401/403/404/409/422/500), `@SecurityRequirement`
- DTOs/Enums: `@Schema` pro Feld (description + example)
- `ErrorDTO` + errorCode-Katalog dokumentieren
- **Gilt für ALLE Controller, DTOs und Enums ohne Ausnahme**

## Docker (MUSS)
- Dockerfile + docker-compose (App + MariaDB optional Runtime)
- **Docker-Configs werden bereitgestellt, aber es wird NICHT vorausgesetzt, dass Docker läuft**
- `docker compose up --build` ist optional für manuelle Nutzung/Deployment
- Health: `/api/v1/actuator/health`