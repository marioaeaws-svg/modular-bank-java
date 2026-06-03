# modular-bank-java

Monolito modular bancario implementado en Java / Spring Boot 3. Referencia técnica para migración a microservicios.

## Requisitos
- Java 17+
- Maven 3.9+
- Docker

## Ejecutar

```bash
docker-compose up -d
mvn spring-boot:run
```

## Módulos

| Módulo | Schema | Interfaz pública |
|---|---|---|
| auth | auth.* | — (solo JWT) |
| accounts | accounts.* | AccountsService |
| transfers | transfers.* | — (orchestrador) |
| notifications | notifications.* | NotificationsService |
| audit | audit.* | AuditService |

## Arquitectura

Cada módulo tiene capas `domain / application / infrastructure / api`.
Los módulos solo se comunican a través de las interfaces en `application/`.
Ningún módulo accede directamente al schema de otro módulo en la DB.

## Migración a microservicios

Ver `README-migration.md` en cada módulo. Orden recomendado:
1. notifications
2. audit
3. auth
4. accounts
5. transfers
