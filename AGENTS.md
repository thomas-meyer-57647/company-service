# AGENTS.md – company-service

## Stack
- Spring Boot 4.0.2, Java 21, Maven
- application.properties (keine yml)
- Spring Web (MVC), Spring Data JPA, Validation
- MariaDB, Flyway (Schema kommt von Flyway), Hibernate ddl-auto=validate
- IDs: UUID/ULID als String. locationId ist global eindeutig.
- OpenAPI/Swagger via springdoc-openapi (springdoc 3.x für Spring Boot 4.x)
- Actuator: health/info/metrics/prometheus
- Soft-Delete (trashedAt/trashedBy), Restore
- Optimistic Locking (@Version)

## API Routen (fix)
- Company: /api/v1/companies/{companyId}/...
- Location: /api/v1/location/{locationId}/...

## Invarianten
- Genau 1 Hauptlocation pro Company (mainLocationId).
- Hauptlocation muss existieren, OPEN und nicht trashed sein.
- Mindestens eine OPEN Location pro Company muss immer existieren.
- Hauptlocation darf nicht geschlossen oder getrashed werden.
- Company trash => cascade trash aller Locations.
- Location-Endpunkte ohne companyId müssen intern company-scharf prüfen.

## Definition of Done
- mvn test grün
- Flyway Migrations laufen
- Swagger UI erreichbar
- Alle Endpoints haben OpenAPI-Annotationen + Examples + Fehlercodes
