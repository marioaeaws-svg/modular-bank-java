# auth — Migration Guide

## Public interface
None — auth is consumed via JWT only. No module calls auth at runtime.

## Consumers
All modules validate JWTs via the shared `JwtAuthFilter` in `shared/`.

## To extract as microservice
1. Create `auth-service` with the same DB schema
2. Move `JwtUtil` to a shared library published to your artifact registry
3. All other services continue validating JWTs locally using the shared library
4. Only `POST /auth/refresh` hits auth-service — all other endpoints are stateless
5. Rotate JWT secret via env var in each service simultaneously
