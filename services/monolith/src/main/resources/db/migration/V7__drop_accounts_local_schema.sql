-- Contract phase of the expand-migrate-contract strategy documented in
-- docs/evidencia/paso-1/05-estrategia-migracion-datos.md: accounts.accounts has already been
-- backfilled into the accounts-service's own database and the monolith's
-- AccountsService bean now calls that microservice over HTTP instead of
-- reading this table. In a real rollout this migration would only ship
-- after a bake/verification period confirming the cutover was correct.

DROP TABLE IF EXISTS accounts.accounts;
DROP SCHEMA IF EXISTS accounts;
