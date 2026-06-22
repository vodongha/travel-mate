# Travel Mate — CLAUDE.md

Guidance for Claude (and humans) working in this repo.

## Project overview

Group-trip planning & shared-expense manager. A group plans a trip together (timeline,
transport, lodging, checklists) and tracks **planned budget vs actual spending**, a **shared
fund**, and **who-owes-whom settlement** across **multiple currencies**.

- **Repo:** https://github.com/vodongha/travel-mate (private)
- **Backend:** Spring Boot 3.x · Java 21 REST API (this repo)
- **Mobile:** Flutter — separate repo: https://github.com/vodongha/travel-mate-app
- **Database:** Oracle Autonomous Database (ADB) Free, Flyway-managed
- **Host:** **Fly.io** (app `trippo-api`, region `sin`, 512MB) — serves the API at `/api/v1` **and**
  the bundled Flutter web client at `/` (same origin). Live: https://trippo-api.fly.dev ·
  https://trippo.io.vn. Android app is published as `vn.trippo.mate`.
- **Status:** **Shipped — v1.0.0 in production.** Backend M1–M8 + the Flutter app (M9, Android + Web)
  are complete and deployed; this section now records the architecture, not a roadmap.
  - **M1** foundation: `BaseEntity`, JPA auditing, UUID v7, `MoneyService`, RFC 7807 envelope, Flyway.
  - **M2** auth: BCrypt + JWT access, DB refresh tokens (rotation + reuse detection), Google ID-token
    verify, email-verify/password-reset, `/users/me`, FCM devices, rate-limit/CORS/body-size hardening.
  - **M3** trips & members: trip CRUD, central `TripAccessGuard` (OWNER>EDITOR>VIEWER; uniform 404 /
    403), ghost members, invitation link/QR with atomic accept + in-place ghost→real merge.
  - **M4** planning: places, events (timeline), transport, accommodation, checklist. Per-person
    seats/QRs are not on the leg/stay — they live on the linked **ticket** (see below).
  - **M5** money: budget per category, multi-currency expense with snapshot rate, `ExpenseSplitter`
    on integer minor units (EQUAL/EXACT/PERCENT/SHARES).
  - **M6** fund & settlement: contributions/fund-expenses, derived fund balance, greedy min-cash-flow
    settlement on minor units (fund kept separate from personal settlement).
  - **M7** dashboard & report. **M8** notifications (`SCHEDULED_NOTIFICATIONS`, `@Scheduled`
    dispatcher, Firebase Admin SDK `FcmSender`; `app.fcm.enabled` gates real sending).
  - **Settings/app slice:** public bilingual privacy page `GET /api/v1/privacy?lang=vi|en`
    (router-only `com.travelmate.legal`, framable — own `@Order(1)` chain); `POST
    /auth/change-password`; `USERS.PHONE` (V8) + profile fields on `/users/me`; `DELETE /users/me`;
    exchange-rate table `GET /rates` + `POST /rates/refresh` (12h `@Scheduled`).

  ### Recent architecture (post-launch refinements)
  - **One canonical classification enum `Category`** (`common.entity.Category`). The former
    `EventType`/`PlaceType`/`TicketType` were collapsed into it (V15 remaps legacy values —
    HOTEL→ACCOMMODATION, RESTAURANT→FOOD, ATTRACTION→SIGHTSEEING, AIRPORT/STATION→TRANSPORT,
    ticket EVENT→ACTIVITY). `TransportType` (FLIGHT/TRAIN/…) stays — it's a sub-type, not a category.
  - **Polymorphic expense→itinerary link.** An expense attaches to any itinerary item via
    `(ITINERARY_KIND, ITINERARY_ID)` where kind ∈ EVENT/TRANSPORT/ACCOMMODATION (V13 replaced the
    old single `EVENT_ID` FK; validated in `ExpenseService`, no cross-table FK).
  - **Group tickets.** `TICKETS.MEMBER_ID` is nullable (V14) — null = a shared ticket owned by the
    whole trip (request `shared=true`, needs EDITOR); surfaced in everyone's `/tickets/mine`.
  - **Ticket→itinerary link.** A ticket is the per-person (or group) scannable pass — boarding pass,
    e-ticket, entrance pass — and attaches to a leg/stay/event via `(ITINERARY_KIND, ITINERARY_ID)`,
    mirroring the expense link (V17, validated in `TicketService`). So one flight leg has one ticket
    per passenger (each with their seat + QR). Consequently **per-person `SEAT`/`QR_DATA` were dropped
    from `TRANSPORTS`, and `QR_DATA` from `ACCOMMODATIONS`** (they duplicated the ticket); a ticket's
    `QR_DATA` is now optional (a seat-only boarding pass).
  - **Place ↔ itinerary delete sync.** Deleting an event (or clearing its location) soft-deletes the
    place when no other event uses it; deleting a place unlinks it from events (they keep, lose
    location). See `EventService.pruneOrphanPlace` + `PlaceService.delete`.
  - **Migrations run V1–V15.** Integration tests (`*IT`) run against a docker-compose Oracle Free
    (`docker compose up -d` → `./mvnw verify`) and in CI against a `gvenzl/oracle-free` service.

**The full specification — modules, DDL, conventions — lives in [`docs/SPEC.md`](docs/SPEC.md)
and is the source of truth.** This file is the working guide; when the two disagree, SPEC.md wins
(and fix this file). Read SPEC.md §2 (conventions) and §12 (the convention checklist) before
writing any code.

The architecture mirrors a layered service design (the author's day-job pattern):
**controller → service → repository**. Keep that separation.

## Technology stack

| Layer | Technology |
|---|---|
| Language / runtime | **Java 21** |
| Framework | Spring Boot 3.x — Spring Web, Spring Security, Spring Data JPA, Spring Validation |
| Database | Oracle Autonomous Database (ADB) Free |
| Migrations | **Flyway** — versioned, app-managed. **Never** `ddl-auto=update` in prod (`validate` only) |
| ORM | Hibernate (JPA). Soft-delete filter via `@SQLRestriction` **or** `@Filter` — see Open Decisions |
| Auth | JWT access (short) + refresh (DB-stored, rotation + reuse detection); BCrypt; Google ID-token verify |
| Scheduler | Spring `@Scheduled`; **ShedLock** once >1 instance runs |
| Push | Firebase Cloud Messaging (FCM) |
| Exchange rates | Free provider (frankfurter.app / exchangerate.host), cached per day, manual override allowed |
| Errors | RFC 7807 `ProblemDetail` (Spring Boot 3 built-in) |
| Tests | JUnit 5 + **Testcontainers** (integration, from M1) |
| Build tool | **Maven** (chosen at the M1 skeleton; mirrors the day-job enterprise-Spring pattern). Use `./mvnw` |

## Money rules (non-negotiable)

This app moves real money across currencies. These are not style preferences.

- **Amounts are `BigDecimal`, column `NUMBER(19,4)`. Never `float`/`double`.** Rates are `NUMBER(19,8)`.
- **Every money transaction snapshots its rate.** An expense/contribution stores `CURRENCY`,
  `AMOUNT` (original), `EXCHANGE_RATE` (to the trip's `BASE_CURRENCY` at spend time), and
  `AMOUNT_BASE = AMOUNT * EXCHANGE_RATE`. The rate is **never recomputed** later. If
  `CURRENCY == BASE_CURRENCY` then `EXCHANGE_RATE = 1`.
- **Rounding is fixed:** compute `AMOUNT_BASE` with `RoundingMode.HALF_UP`, scale 4. Pin this in
  one `MoneyService` and test it — otherwise totals drift between environments.
- **Every aggregate — budget, fund balance, settlement — runs on `AMOUNT_BASE`** (one currency).
- **Settlement math runs on integer minor units** (a `long`), not divided `BigDecimal`, so rounding
  never drifts; convert back to `BigDecimal` only at the boundary. The `EQUAL` split gives any
  remainder to a **deterministically ordered** first member (order by member id) so the shares sum
  exactly to `AMOUNT_BASE`.
- **Fund balance is derived** = `SUM(FUND_CONTRIBUTIONS.AMOUNT_BASE) − SUM(FUND_EXPENSES.AMOUNT_BASE)
  − SUM(EXPENSES.AMOUNT_BASE WHERE PAID_FROM_FUND=1)`. **Never store a balance column** (race-prone).
- An expense is **either** a fund expense (`PAID_FROM_FUND=1` / `FUND_EXPENSES`) **or** a personal
  debt-creating expense — **never both**, never double-counted.
- If you touch money math, rate snapshotting, or the settlement engine, **write the test first**.

## Identifiers — ID vs RID (non-negotiable)

- **`ID` (`NUMBER(19)`)** — internal PK / FK / joins only. **Never returned from the API.**
- **`RID` (`VARCHAR2(36)`, UUID v7)** — the public identifier in URLs and responses. Resolve an
  incoming `{rid}` → internal `ID` before doing any work. Every table has `UK_<TABLE>_RID`.
- **UUID v7 is generated in Java** (time-ordered, better index locality than v4). Oracle's
  `SYS_GUID()` is **not** v7, and Java 21 has no built-in v7 — use a generator library (e.g.
  `java-uuid-generator`) or a small hand-rolled v7, set in `@PrePersist`.

## Architecture & conventions

```
controller → service → repository → JPA/Hibernate → Oracle ADB
```

- **Controllers are thin.** Parse the request, resolve `RID`, pull the current user, delegate to a
  service, shape the response (envelope `{ data, error, meta }`; errors are RFC 7807). No business logic.
- **Services hold business logic.** Compute `AMOUNT_BASE`, enforce invariants, snapshot rates.
- **Repositories are DB-only.** Spring Data JPA; no business logic.
- **API base path `/api/v1`.** Resources addressed by `RID`, nested under trips
  (`/trips/{tripRid}/expenses`, `PATCH /expenses/{rid}`). Pagination `?page=&size=&sort=`.
- **`BaseEntity` (`@MappedSuperclass`)** carries `ID, RID, CREATED_AT/BY, UPDATED_AT/BY, VERSION,
  IS_DELETED`. Auditing via JPA `@CreatedDate/@LastModifiedDate/@CreatedBy/@LastModifiedBy`.
  Optimistic lock via `@Version` → `OptimisticLockException` maps to **409**.
- **Idempotency:** money-creating POSTs (expense, fund contribution) accept an `Idempotency-Key`
  header; store the key + result and replay on a repeat. A repeat key with a **different** payload
  is a `422`, not a replayed result. (Needs the `IDEMPOTENCY_KEYS` table — see Open Decisions.)

### Authorization — one central guard

- **Every trip-scoped endpoint** must verify the current user is a member of the trip with a
  sufficient role (`OWNER` > `EDITOR` > `VIEWER`), **including** when the entry point is a child
  resource's `{rid}`.
- **Do this in one place: `TripAccessGuard`** — pattern `resource rid → load → derive tripId →
  check membership + role`. Never re-implement this per service (the easiest place to leak access).
  Prefer resolving the child `rid` → `tripId` in the **same** membership query.
- Return a **uniform 404** for both "not found" and "not your trip" (no existence leak).
  `@RequireTripRole(MIN_ROLE)` annotation/aspect over the guard.

### Member-centric money

Every money reference (`PAYER_ID`, `EXPENSE_SHARES.MEMBER_ID`, `FUND_CONTRIBUTIONS.MEMBER_ID`)
points at **`TRIP_MEMBERS.ID`, not `USERS.ID`** — so the group can split with **ghost members**
(people without an account). When a ghost later joins via invitation with the same email, **merge**
the ghost membership into the real one (re-point all money references, then soft-delete the ghost) —
see Open Decisions; don't leave orphaned references.

### Auth & hardening

- BCrypt password hashing. Refresh tokens **stored in DB** (hashed) and **rotated** on every
  `/auth/refresh`; a reused (already-rotated) token means compromise → revoke the session/family.
- Rate limiting (IP + user, especially auth endpoints), CORS whitelist (no `*` with credentials),
  request body-size limit. In-memory rate limiting is fine on a single instance.

### Time & timezone

- Store **all** timestamps UTC (`TIMESTAMP`). Display in `TRIP.TIMEZONE` (or the location's tz for
  cross-tz flights). Oracle `TIMESTAMP` carries no zone — set
  `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` so the driver doesn't shift by the host tz.

## Deployment (Fly.io)

Single Fly.io app **`trippo-api`** (region `sin`, 512MB, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65`).
It serves the API under `/api/v1` **and the bundled Flutter web client at `/`** (same origin → the
web app needs no CORS; `SpaWebConfig` falls back to `index.html` for client-side routes, never for
`/api/**` or real asset files).

- **CI/CD:** `.github/workflows/ci.yml` runs `./mvnw verify` against an Oracle Free service container
  on `develop`. `deploy.yml` runs on push to `master` (and `workflow_dispatch`): it checks out the
  **app repo's `master`**, builds the Flutter web with `--dart-define=SAME_ORIGIN=true`, bakes it into
  `classpath:/static/`, then `flyctl deploy`. **An app-only release needs a backend (re)deploy** to
  refresh the live web (`gh workflow run deploy.yml --repo vodongha/travel-mate --ref master`).
- **Fly secrets:** `WALLET_TAR_B64` (Oracle wallet tar.gz, decoded by `scripts/fly_entrypoint.sh`),
  `ORACLE_PASSWORD`, `FCM_CREDENTIALS_B64` (Firebase service account), `CORS_ALLOWED_ORIGINS`,
  JWT secret + Google client-id. The `prod` profile uses schema `TRAVEL_MATE`.
- The Oracle wallet and the Firebase service-account JSON are **never committed** (gitignored).

## Open decisions — settle these before/while coding (from spec review)

These came out of the spec review; resolve and then delete the item (record the choice here).

1. **Soft-delete filter: `@SQLRestriction` vs `@Filter`.** `@SQLRestriction` is global and **cannot
   be turned off per query**, which hides soft-deleted rows that settlement/report legitimately need
   (e.g. a member who left but still owes). Strongly consider `@FilterDef`+`@Filter` (toggleable) for
   the settlement/report entities, or dedicated native read paths. **Decide before M5.**
2. **`IDEMPOTENCY_KEYS` table is missing from the DDL.** Define `(USER_ID, KEY, ENDPOINT,
   REQUEST_HASH, RESPONSE_BODY, STATUS_CODE, CREATED_AT)` + a TTL cleanup job; same-key/different-payload → 422.
3. **Ghost → real-user merge flow** is unspecified. Define how accepting an invitation with a
   matching email re-points `PAYER_ID` / `EXPENSE_SHARES` / `FUND_CONTRIBUTIONS` and soft-deletes the ghost.
4. **Email provider** for verify/reset is not in the stack. Pick a free relay (Brevo/Resend free
   tier, etc.) — blocks M2.
5. **Settlement output is "minimised", not provably minimal** (min-transactions is NP-hard). Greedy
   is fine; phrase it as "minimised" in API/UI, and run it on integer minor units.
6. **Fund vs personal settlement are reported separately** (by spec). Decide whether to also expose a
   **merged** "final who-owes-whom" view, since groups usually want one number.
7. **Refresh-token reuse detection** with a flat `AUTH_TOKENS` table can only revoke *all* of a user's
   sessions. Add a `FAMILY_ID`/session chain if you want to revoke just the compromised chain.

## Oracle gotchas (ADB) — read before debugging these

Mostly learned on the sibling `family-budget` project (Python/Oracle), but the DB behaviour is the same.

- **Oracle has no native boolean.** `IS_DELETED`/flags are `NUMBER(1)`; filter with `= 0`/`= 1`, not SQL `IS`.
- **Oracle stores `''` as `NULL`.** Inserting `''` into a `NOT NULL` column is `ORA-01400`. For
  "optional text" make the column nullable and write `null`, never `""` (e.g. a Google-only user's
  password hash).
- **Oracle enforces `VARCHAR2` length.** Size columns for the longest real value or you get `ORA-12899`
  at runtime (e.g. enum strings like `RENTAL_VEHICLE`, `transfer_out`).
- **Partial unique under soft delete:** business uniques (e.g. `USERS.EMAIL`) must be function-based
  so they only apply when not deleted —
  `CREATE UNIQUE INDEX UK_USERS_EMAIL ON USERS (CASE WHEN IS_DELETED = 0 THEN EMAIL END);`
- **`TRIP_MEMBERS` unique `(TRIP_ID, USER_ID) WHERE USER_ID NOT NULL`:** Oracle treats NULLs as
  distinct (multiple ghosts allowed), but make the intent explicit with a function-based index
  `(CASE WHEN USER_ID IS NOT NULL THEN TRIP_ID END, USER_ID)`.
- **Accept-invitation must be an atomic `UPDATE`**, never read-check-write:
  `UPDATE TRIP_INVITATIONS SET USED_COUNT = USED_COUNT + 1 WHERE TOKEN = ? AND USED_COUNT < MAX_USES
  AND EXPIRES_AT > now` then check rows affected.
- **`@SQLRestriction("IS_DELETED = 0")`** (not the deprecated `@Where`) — but see Open Decision #1.
- **ADB Free auto-stops after ~7 days idle** and the **Always Free VM can be reclaimed when idle** —
  two stacked idle-reclaim risks for a trip app used in bursts. Plan a keep-alive ping + uptime
  monitor + a known backup/restore path. This is the biggest operational risk of the Oracle path.

## Testing

- **Framework:** JUnit 5. Integration tests use **Testcontainers** from M1.
  - Testcontainers has an **Oracle Free** image (`gvenzl/oracle-free`); prefer it so tests prove
    Oracle behaviour (identity, length, NULL/`''`, function-based indexes) that an in-memory DB hides.
- **Required** for any DB-mutating operation and all money logic.
- **The Settlement Engine needs dedicated unit tests**, especially the `EQUAL` remainder split and
  multi-currency `AMOUNT_BASE` aggregation.

## Coding conventions

- Standard Java: `PascalCase` types, `camelCase` members, `UPPER_SNAKE` constants. 4-space indent.
- DTOs (`dto/` Request/Response) at every API boundary — never expose entities or internal `ID`.
- Bean Validation (`@Valid`) on inputs. Keep methods short and single-purpose.
- Comments explain **why**, not what.

### Language — English only

All source — comments, names, string literals, config, commit messages — is **English**.
User-facing strings in the Flutter app may be Vietnamese/bilingual; this rule is about the codebase.

### No secrets / PII in code

Never hardcode secrets or PII. Config comes from environment / `.env` (gitignored) and container
secrets. The Oracle wallet and `firebase-service-account.json` are **never** committed (`.gitignore`
covers `wallet/`, `*.pem`, `*.jks`, `*.json` service accounts, `.env`). If a secret is ever
committed, rewrite history and rotate it.

## Git workflow

Two long-lived branches (mirrors the other personal repos — `family-budget`, `vodongha-personal`):

| Branch type | Base | PR target | Use |
|---|---|---|---|
| `feature/short-description` | `develop` | `develop` | New feature |
| `bug/short-description` | `develop` | `develop` | Non-urgent bug fix |
| `hotfix/short-description` | `master` | `master` | Urgent production fix |

- **`master` is release-only — never commit directly.** Work goes through `develop`; cut a release
  by PR-ing `develop → master`. `.github/workflows/sync-develop.yml` merges `master → develop` after
  every push to `master`, so feature branches start from the latest released code.
- Merge with **merge commits** (no squash/rebase).
- Commit messages: short imperative subject, bullet body for meaningful changes.

### Author identity & co-authorship

Personal repo — set the **personal identity locally** (the machine's global git defaults to the
cisbox company email):

```bash
git config --local user.name "vodongha"
git config --local user.email "vodongha@hotmail.com"
```

AI-assisted commits are **authored by `vodongha`** with **Claude as the committer**:

```bash
GIT_COMMITTER_NAME="Claude Opus 4.8" GIT_COMMITTER_EMAIL="noreply@anthropic.com" \
  git commit --author="vodongha <vodongha@hotmail.com>" -m "..."
```

## Built with

[Claude Code](https://claude.ai/code) by Anthropic. 🤖
