# TravelMate

Group-trip planning & shared-expense manager — a group (family, friends, colleagues)
plans a trip together: timeline, transport, lodging, checklists, then tracks **planned
budget vs actual spending**, a **shared fund**, and **who-owes-whom settlement** across
**multiple currencies**.

- **Backend:** Spring Boot 3.x · Java 21 (this repo) — REST API
- **Database:** Oracle Autonomous Database (ADB) Free, via Flyway-managed schema
- **Mobile:** Flutter (Android + iOS) — separate repo: [vodongha/travel-mate-app](https://github.com/vodongha/travel-mate-app)
- **Docs:** full spec in [`docs/SPEC.md`](docs/SPEC.md) · contributor/agent guide in [CLAUDE.md](CLAUDE.md)

> **Status:** **M1 (foundation) + M2 (auth) implemented** on a Maven / Spring Boot 3 / Java 21
> backend running against the Oracle ADB (schema `TRAVEL_MATE`). M2 adds email/password + Google
> sign-in, JWT access tokens, DB-stored refresh tokens with rotation & reuse detection,
> email-verify / password-reset, `/users/me`, FCM device registration, and rate-limit/CORS/body-size
> hardening. **M3 (trips & members) is next.** The full source of truth is [`docs/SPEC.md`](docs/SPEC.md).

---

## What it does (planned scope)

| Area | Summary |
|---|---|
| **Auth** | Email/password (BCrypt) + Google Sign-In → JWT access + DB-stored refresh token (rotation + reuse detection). Email verify & password reset. FCM device registration. |
| **Trips & members** | Trips scoped by membership with roles `OWNER`/`EDITOR`/`VIEWER`. **Ghost members** (people without the app) can be split with. Invite by link/QR. |
| **Planning** | Timeline events, transport, accommodation, places (OpenStreetMap), checklists. |
| **Money** | Multi-currency with **snapshot exchange rates** (free provider + manual override). Planned **budget** per category vs **actual expenses**. |
| **Shared fund** | Contributions and fund expenses; **balance is always derived by aggregation**, never stored. |
| **Settlement** | Per-member net balance + **minimised** debt transactions (greedy min-cash-flow). |
| **Dashboard & report** | Countdown, budget vs actual, fund balance, next event; end-of-trip report. |
| **Notifications** | Scheduled FCM reminders (pre-trip 30/7/1 days, event/check-in, debt). |

The full module-by-module specification, DDL, and conventions live in [`docs/SPEC.md`](docs/SPEC.md).

---

## Non-negotiable rules (see [`docs/SPEC.md`](docs/SPEC.md) §2, §12)

- **Money is `BigDecimal` / `NUMBER(19,4)`** — never `float`/`double`. Rates are `NUMBER(19,8)`.
- **Every aggregate (budget, fund, settlement) runs on `AMOUNT_BASE`** (the trip's base currency);
  the exchange rate is **snapshotted** at spend time and never recomputed.
- **Never expose internal `ID`.** Public identifiers are `RID` (UUID v7); resolve `{rid}` → `ID`
  before any work.
- **All timestamps stored UTC**, converted to the trip timezone for display.
- **Every trip-scoped endpoint checks membership + role** through one central `TripAccessGuard`.
- **Soft delete** (`IS_DELETED`) + **partial unique** indexes for business keys.
- **Fund balance is derived** (`SUM(contributions) − SUM(fund expenses)`), never a stored column.
- An expense is **either** a fund expense **or** a personal (debt-creating) expense — never both.
- **Flyway** for schema — no `ddl-auto=update` in production.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.x — Spring Web, Security, Data JPA, Validation |
| Database | Oracle Autonomous Database (ADB) Free |
| Migrations | **Flyway** (versioned, app-managed) |
| Auth | JWT access + refresh (rotation/reuse detection); BCrypt; Google ID-token verify |
| Scheduler | Spring `@Scheduled` (+ ShedLock when running multi-instance) |
| Push | Firebase Cloud Messaging |
| Exchange rates | Free provider (frankfurter.app / exchangerate.host), daily cache, manual override |
| Errors | RFC 7807 `ProblemDetail` |
| Tests | JUnit 5 + **Testcontainers** (integration), unit tests for the Settlement Engine |
| Host (planned) | Oracle Cloud Always Free VM (Ampere A1 preferred) |

---

## Project structure (target — see [`docs/SPEC.md`](docs/SPEC.md) §3)

```
com.travelmate
├── common        # BaseEntity, exceptions, security/JWT, audit, money (rate provider)
├── user · trip · member · place
├── timeline · transport · accommodation
├── budget · expense · fund · settlement
├── checklist · dashboard · notification
```

Each module is a vertical slice: `*Controller → *Service → *Repository → *Entity` + `dto/` + `mapper/`.

---

## Quick start

Requires **JDK 21**. The build uses the bundled Maven wrapper (`./mvnw`) — no local Maven needed.

```bash
# 1. Unit tests (no database / Docker needed)
./mvnw test

# 2. Integration tests (Testcontainers — needs a running Docker daemon)
./mvnw verify

# 3. Run against the Oracle ADB
cp .env.example .env                         # set ORACLE_PASSWORD, DSN, etc.
# place the unzipped ADB wallet under ./wallet/  (gitignored — never commit)
# one-time: create the app's schema as ADMIN (OCI → Database Actions → SQL):
#   scripts/create_schema.sql   (creates schema-only user TRAVEL_MATE)
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
# then: curl http://localhost:8000/api/v1/ping  ->  {"data":{"status":"ok"}}
```

The app connects as `ADMIN` via the wallet and switches `CURRENT_SCHEMA` to `TRAVEL_MATE`, so
several apps can share one ADB (mirrors the sibling `family-budget`). See `.env.example` and
`src/main/resources/application-local.yml.example`.

---

## Milestones (see [`docs/SPEC.md`](docs/SPEC.md) §9)

1. **M1 — Foundation ✅:** skeleton, `BaseEntity`, JPA auditing, soft delete, Flyway, exception
   handler, envelope response, Testcontainers from day one.
2. **M2 — Auth ✅:** email/password + Google + JWT/refresh + verify/reset; `users/me`; FCM devices.
3. **M3 — Trip & members:** CRUD, ghost members, invitation link/QR, `@RequireTripRole`.
4. **M4 — Planning:** places, events, transport, accommodation, checklist.
5. **M5 — Money:** rate snapshot, budget, expense + shares.
6. **M6 — Fund & settlement:** contributions/expenses, derived balance, settlement engine.
7. **M7 — Dashboard & report.**
8. **M8 — Notifications:** scheduled FCM job.
9. **M9 — Flutter app.**

---

## Git workflow

Two long-lived branches (mirrors the other personal repos):

| Branch type | Base | PR target | Use |
|---|---|---|---|
| `feature/*` | `develop` | `develop` | New feature |
| `bug/*` | `develop` | `develop` | Non-urgent fix |
| `hotfix/*` | `master` | `master` | Urgent production fix |

`master` is release-only — **never commit directly**. `sync-develop.yml` merges `master → develop`
after every push to `master`. See [CLAUDE.md](CLAUDE.md) for identity & co-authorship rules.

---

## License

[MIT](LICENSE)

---

## Built with

[Claude Code](https://claude.ai/code) by Anthropic. 🤖
