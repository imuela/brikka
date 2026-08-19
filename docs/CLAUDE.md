# BRIKA — CLAUDE CODE INSTRUCTIONS

## 1. Mission

Implement Brika V1 strictly according to `/docs`.

Read the documentation before making architectural or business changes.

## 2. Source of truth

Priority:
1. approved decisions;
2. BRIKA_MASTER_SPEC.md;
3. FUNCTIONAL_SPECIFICATION.md;
4. technical/database/API/security specifications;
5. implementation details.

## 3. Forbidden

Do not:
- change business rules silently;
- weaken tenant isolation;
- expose internal information to CLIENT;
- store secrets in source control;
- introduce arbitrary frameworks;
- delete historical document versions without explicit policy;
- skip tests for critical functionality.

## 4. Architecture

Frontend:
Angular + TypeScript

Backend:
Java + Spring Boot

Database:
PostgreSQL

Migrations:
Flyway

Infrastructure:
Docker + CI/CD

## 5. Development method

For each task:
1. read relevant docs;
2. inspect existing code;
3. plan;
4. implement;
5. test;
6. review security;
7. update documentation if a decision changes;
8. report files changed and tests executed.

## 6. Tenant isolation

Tenant isolation is mandatory.

Never trust company_id from the frontend.

Resolve tenant from authenticated identity/context and validate every resource.

## 7. Client Portal

CLIENT is a separate security boundary.

Default visibility is private.

Only explicitly published information may be exposed.

## 8. Documents

Use versioning.

Do not overwrite historical versions.

Files are private resources and require authorization before access.

## 9. Testing

Every feature must include appropriate tests.

Security tests must include cross-tenant and unauthorized-access scenarios.

## 10. Change control

If implementation requires changing an approved business rule:
STOP, explain the conflict, propose the change, and update the decision log only after approval.

## 11. Code quality

Prefer clear modular code over premature abstraction.

Avoid unnecessary dependencies.

Keep domain logic out of controllers.

Use consistent error handling and validation.

## 12. Completion criteria

A task is complete only when:
- implementation works;
- tests pass;
- security requirements are met;
- documentation is updated where needed;
- no known regression is introduced.
