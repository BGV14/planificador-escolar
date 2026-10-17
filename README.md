# Shared school planner

React / TypeScript provides a Spanish teacher workspace with a responsive exact-date calendar, ownership-aware editing and explicit import confirmation. Java 21 / Spring Boot provides authenticated shared-school persistence, cumulative legacy imports and genuine monthly XLSX/PDF exports. No cloud deployment has been performed.

## Run locally

1. Set `JAVA_HOME` to JDK 21. Set `PLANNER_ADMIN_USERNAME`, `PLANNER_ADMIN_PASSWORD` and `PLANNER_SCHOOL_NAME` in your local process environment, never source control. Passwords require 12+ characters and at most 72 UTF-8 bytes. Optional `PLANNER_SCHOOL_YEAR` defaults to 2026 and `PLANNER_GRADE` to 6.
2. From the workspace root, run `./backend/mvnw.cmd -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev` with the Maven cache settings below. The explicit `dev` profile uses the persistent local H2 database under `backend/.tools`; it is not a production substitute.
3. First startup against an empty database creates one school, one administrator and the configured academic context. Only PRIMARY grade 6 receives the four-area public grade-six catalog; other contexts start empty. Later startups never reset credentials or overwrite existing planning. Remove bootstrap secrets after provisioning. An empty database without bootstrap variables remains inaccessible; there is no public signup.
4. In a second terminal, enter `frontend`, run `npm ci`, then `npm run dev`. Open the local URL printed by Vite (normally `http://127.0.0.1:5173`) and sign in with the provisioned account. Vite proxies `/api` to `127.0.0.1:8080`; credentials and private planning data are not stored in localStorage. There is no demo login or fabricated saved plan.

The frontend uses Tailwind CSS 4 and locally maintained shadcn-pattern Button/Input/Card/Dialog primitives. Dialogs use Radix for keyboard focus, Escape and focus restoration; buttons use Radix Slot and CVA. Build with `npm run build`. The root Dockerfile builds React and embeds its compiled assets in the Spring Boot JAR **inside the image build only**, so the frontend and `/api` share one origin. Local development still uses Vite; no generated frontend files are copied into backend source.

Production is the default profile and requires `PLANNER_DATABASE_URL` (a PostgreSQL JDBC URL), `PLANNER_DATABASE_USERNAME` and `PLANNER_DATABASE_PASSWORD`. It never falls back to H2. Use HTTPS and a same-origin reverse proxy, private database access, backups, secret management and login/request rate limiting before public deployment. JDBC login sessions survive restarts and support multiple instances sharing the same database. The session cookie is HttpOnly, SameSite=Strict and Secure in production. No CORS wildcard is enabled. Local React development must proxy `/api` to port 8080 instead of using cross-origin cookies.

## Render deployment preparation

Deployment files are prepared locally; they are **not evidence of a successful cloud deployment or production readiness**. The Blueprint creates one free Docker web service and no database. Do not deploy until an explicitly authorized, durable PostgreSQL service and its credentials are available.

1. Publish only application source, build configuration and synthetic automated tests to the authorized private repository. Never upload synced `sources/`, local `.tools/` databases, exports, credentials, `node_modules`, `dist`, or local workflow records. `.gitignore` excludes these, and `.dockerignore` allows only image build inputs.
2. Select the repository in Render using the authorized account session. Keep the service on the Free instance plan. `render.yaml` disables automatic deploys; later deployments remain explicit decisions.
3. Supply the three `PLANNER_DATABASE_*` values through Render secret environment settings. The URL must be a PostgreSQL **JDBC** URL, not a raw `postgres://` URI. Use the database provider's verified TLS/CA settings, preferably `sslmode=verify-full`; do not disable certificate verification or put passwords in the URL, source, image build arguments or logs. The database must be reachable from Render under its explicitly configured network policy.
4. Bootstrap an empty school only through separately supplied temporary administrator credentials, as described under local startup. The Blueprint intentionally contains no bootstrap account or password. Remove bootstrap secrets after provisioning. Without provisioning, the application can serve its login shell but nobody can sign in.
5. Validate PostgreSQL migrations and session persistence, HTTPS login/CSRF/logout, ownership boundaries, import confirmation and representative XLSX/PDF exports on the deployed service before entrusting it with school data.

| Setting | Boundary |
|---|---|
| Runtime | Non-root Java 21 process; `prod` profile; binds `0.0.0.0` using Render's `PORT` variable |
| Public routes | GET/HEAD of `/`, `/index.html` and `/assets/**`; authenticated planning remains under `/api/**`; login/logout still require CSRF |
| Proxy trust | The Blueprint opts into `SERVER_FORWARD_HEADERS_STRATEGY=framework` for Render's HTTPS-terminating ingress. This trusts forwarded headers: allow no direct/untrusted ingress to the application port, and verify the edge replaces/sanitizes client-supplied forwarding headers. Outside this trusted boundary leave the variable unset (`none`). |
| Cookies | Secure, HttpOnly, SameSite=Strict unchanged; open the app directly as a top-level HTTPS page. Cross-site iframe/embedded use is not supported or verified. |
| Memory | 256 MiB maximum heap with bounded JVM native-memory settings, 20 HTTP worker threads and 5 database connections. These are starting limits for a 512 MiB instance, not an export load-test result; concurrent or very large exports can still exhaust memory. |
| Health check | `/` confirms the frontend shell is served; it is not a database readiness probe. Startup migrations require the database. |

Render's free service can sleep after inactivity and loses local filesystem changes. Do not use H2 or filesystem backups there. Its free PostgreSQL offer expires after 30 days, so it is deliberately absent from this Blueprint. Free hosting is a preview/hobby deployment, not a reliability or production-service guarantee. See [free instance limits](https://render.com/docs/free), [Docker deployment](https://render.com/docs/docker), [port binding and TLS ingress](https://render.com/docs/web-services), [Blueprint secrets](https://render.com/docs/blueprint-spec#prompting-for-secret-values) and [Spring proxy handling](https://docs.spring.io/spring-boot/how-to/webserver.html).

**Still required:** authorized durable database selection, PostgreSQL integration proof, forwarding-header spoof checks at the actual ingress, login/request/export rate limits, memory/load verification, encrypted operational backups with a tested restore, password recovery and a deployment smoke test. The Docker build skips tests; run the checks below separately before deployment. No Docker daemon was available for local image verification during preparation.

## Independent plans by year, level and stage

Use the **Plan académico** selectors to open an existing year, level and age/grade. Administrators can expand **Crear plan para otro año, nivel o etapa**, select (for example) grade 3 of Primary in 2026 or 2027, and confirm **Crear plan vacío**. Each context starts empty: first create its areas in **Escuela y horarios**, then its custom topics in **Banco de temas**. Topics, dates, blocks and responsible teachers can be edited independently in each plan; no curriculum is copied automatically. Teachers can open existing plans but only administrators create them. Initial age zero is **Menores de 1 año**, never grade zero.

Switching contexts clears the current data, area filter, editor and import preview. Requests and forms capture the explicit context ID; late loads, mutation completion callbacks and downloads from a previous selection cannot replace the current workspace. School users remain school-wide. The sidebar, workspace heading and monthly exports identify the selected context.

The school is the tenant identity, not an academic year. A context identifies one school/year/level/stage. Authenticated school members list `GET /api/contexts`; administrators create `POST /api/contexts` with `{year,level,stageValue}`. Years are 1900–2200; levels are `INITIAL` (ages 0–5), `PRIMARY` (grades 1–6) and `SECONDARY` (grades 1–5). `INITIAL` uses `stageKind: "AGE"`, not a grade; age zero is "Menores de 1 año", with cycles I (0–2) and II (3–5). Other levels use `stageKind: "GRADE"`. Metadata includes `id,schoolId,year,level,stageKind,stageValue,cycle,displayLabel,revision,legacyDefault`. No context deletion or automatic curriculum copying is implemented.

Use explicit `?contextId=<id>` on **every** planning resource request, including create/update/delete, `/plan`, `/imports`, `/imports/preview` and `/imports/confirm`. Context IDs never choose a school: authentication always supplies the tenant. Cross-context parent or resource IDs are rejected even within the same school. Session versions remain resource-local; preview revisions and row locks are context-local, so one year does not invalidate another year's preview. Dates and blocks must fit the selected context's year. Whole-school blocks are now whole-context blocks.

`GET /api/plan?contextId=<id>` returns the context metadata above plus `state` (legacy maps) and `warnings`. Session/resource responses and import history include `contextId`. The legacy importer itself is unchanged and is offered only to `PRIMARY` grade 6 contexts because old backup metadata cannot identify a level. Custom topics remain available in all contexts; ordinary session validation does not depend on that importer.

Flyway V3 creates a PRIMARY context from each historical school's stored year/grade and preserves all resource IDs, legacy IDs, owners, session values/versions, blocks, import history and revisions. Historically ambiguous grades outside 1–6 or unsupported years fail migration instead of being guessed. Earlier migrations are unchanged. New contexts start empty; the public sixth-grade catalog is seeded only during first bootstrap for PRIMARY grade 6. Optional bootstrap `PLANNER_LEVEL` defaults to PRIMARY, while `PLANNER_GRADE` means age for INITIAL and grade otherwise.

**Temporary compatibility boundary:** omitted context queries and old service overloads select only the immutable migrated/bootstrap default, never the newest year or a caller-supplied school. Explicit empty/foreign context IDs never fall back. The frontend always sends an explicit context ID, including export and import requests. Only old clients omitting the query retain default-context compatibility; an explicit blank or foreign ID is rejected. `/me.school.schoolYear` and `grade` are historical bootstrap metadata, not a selected planning context. The transitional `grade` plan field is null for INITIAL. The v4 full-school snapshot covers every context, but automatic restoration remains unsupported; it is not a tested disaster-recovery system.

## Frontend API contract

All routes are under `/api`. Authenticated GETs are school-wide readable; caller-supplied school IDs never select the tenant. Mutations return the saved object; deletes return an empty 200. Use JSON except login. Resource IDs are strings. List responses are arrays, with camelCase names.

1. GET `/csrf` → `{token,headerName}`. Send the token in that header on **every** mutation, including login and logout, with same-origin cookies.
2. POST `/login` as `application/x-www-form-urlencoded` with `username` and `password` → 200, or 401. Fetch a fresh CSRF token after login. GET `/me` → `{id,schoolId,username,role,school}`.
3. POST `/logout` → 204; then fetch a fresh CSRF token. Anonymous protected requests return 401; missing CSRF or denied permission returns 403.

| Resource | GET list; POST create; PUT `/{id}` update; DELETE `/{id}` | Request fields |
|---|---|---|
| `/areas` | Administrator only for mutations | `name, ownerId, sharedEdit` |
| `/topics` | Owned/shared area editors | `areaId, name, unit` (1–99), optional `legacyId` (`course\|unit\|zeroBasedIndex`; immutable after creation) |
| `/schedules` | Owned/shared area editors | `areaId, weekday` (ISO Monday=1 through Sunday=7), `startTime, endTime` (`HH:mm`, start before end) |
| `/sessions` | Owned/shared area editors | `topicId, assignment, status, date, notes`; update additionally requires numeric `version` |
| `/blocks` | Owned/shared area editors; school-wide blocks require administrator | `areaId` (null for whole school), `label, kind, startDate, endDate`; kind is `EXAM`, `VACATION`, `HOLIDAY` or `OTHER` |
| `/users` | Administrator GET and POST only | `username, password, role` (`ADMIN` or `TEACHER`); responses never contain password hashes |

Session responses use `exactDate` (request uses `date`) and include `version`, initially 0. Delete a session with `?version=<current version>`. Stale edit/delete → 409 `STALE_SESSION`; reload rather than overwrite. There is one session per topic in the selected academic context. The exact date must be empty/null or a valid ISO date in that context's year. Assignment uses the legacy Spanish month names and status is empty or `Trabajado`. Null means absent; empty strings remain explicit empty values. A session PUT replaces its four fields, so the UI must send all current values.

By default, teachers can mutate only their assigned areas. An administrator can enable `sharedEdit` per area to permit any teacher in the **same school** to edit it. Only administrators assign area owners or manage users. Cross-school references are rejected with 404 (or 403 when the entire operation requires an administrator). Referenced areas/topics cannot be deleted until their child resources are removed; the server returns 409, not cascading data loss.

Calendar ranges are inclusive and must stay within the selected academic context's year. Saving a session on an applicable block returns 409 `BLOCKED_DATE`; the date is never moved. Adding a block over an existing session preserves that session and returns `warnings`; GET `/plan` also returns the current overlaps. UI must display them. No legal holidays or exam dates are assumed or automatically inserted.

### Import preview and confirmation

- GET `/plan` → `{revision,year,grade,state,warnings}`. The four legacy maps include only catalog-linked topics. GET `/sessions` includes custom topics too.
- POST `/imports/preview` with `{json:"<raw backup text>"}` → `{revision,result:{state,preview,warnings},notice}`. The returned merged state is a preview only. Display the incoming-wins notice and counts before confirming.
- POST `/imports/confirm` with `{json:"<same raw backup text>",revision,acknowledgeIncomingWins:true}` → `{revision,result}`. Confirmation revalidates and recomputes against the locked current plan; stale previews return 409 `STALE_PREVIEW`, requiring another preview. All affected topics must be editable, and blocked dates reject the entire transaction. History is written only on successful commit.
- GET `/imports` returns school history `{id,schoolId,userId,importedAt,affectedTopics,revision}`; raw private backups are never retained in history or diagnostics.

Validation errors use 400; importer errors provide `{code:"INVALID_IMPORT",violations:[{path,code}]}`. Conflict/authorization errors provide `{code:<HTTP status>,message}`. Duplicate IDs/names or referenced deletes return 409 `RESOURCE_CONFLICT`. Database constraints also enforce same-school parent references. Flyway validates migration checksums; JDBC uses explicit statements, with no ORM automatic DDL or production schema creation fallback.

## Verify

From `frontend`, run `npm test` and `npm run build`. Focused Vitest checks cover exact-date calendar placement, dense days/weekends, ownership, fresh CSRF after authentication, stale edit messages, and preview/confirmation consent. Source formatting is `npm exec -- prettier --check 'src/**/*.{ts,tsx,css}' '*.json' '*.ts' index.html`.

Run from the workspace root in PowerShell, with a JDK 21 installation available:

```powershell
# Set JAVA_HOME to your JDK 21 root if the existing value points to another version.
$env:MAVEN_USER_HOME = Join-Path $PWD 'backend/.tools/maven'
$env:MAVEN_OPTS = '-Dmaven.repo.local="' + (Join-Path $PWD 'backend/.tools/repository') + '"'
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=LegacyPlanImporterTest test
.\backend\mvnw.cmd -f backend/pom.xml verify
```

The first run downloads the Maven distribution and public dependencies from Maven Central. Wrapper distributions and dependency caches stay in the ignored `backend/.tools` directory when using these settings. Generated build outputs stay in `backend/target`.

PDFBox may scan installed fonts; scope its optional cache by adding `-Dpdfbox.fontcache=<absolute backend/.tools path>` to local Maven checks or the application JVM. Export tests use synthetic data only and write QA samples under `backend/.tools/export-qa`. They parse real workbook cells and PDF text and render PDF pages. HTTP integration tests need permission to bind/connect to local loopback sockets.

## Exports and backup boundaries

All export routes require the authenticated school session, use server-derived tenant filtering and return `Cache-Control: no-store`. They are GET downloads, so they do not require a mutation CSRF header. Foreign-school context IDs and cross-context area IDs return 404. Use an explicit `contextId` for all monthly and v2 exports.

| Route | Result |
|---|---|
| `/api/exports/monthly?contextId=<context>&month=3&areaId=<id>&format=xlsx` | Genuine OOXML workbook generated with Apache POI; omitting area selects all areas of that context |
| `/api/exports/monthly?contextId=<context>&month=3&areaId=<id>&format=pdf` | Paginated PDFBox document with weekday calendar and complete appendices |
| `/api/exports/backup?contextId=<context>&month=3&areaId=<id>&format=json` | PRIMARY grade-6 catalog-only v2 wrapper with independent legacy maps; omit month for that context’s full-year catalog-linked plan |
| `/api/exports/backup?contextId=<context>&format=full-v4` | Version 4 **complete-school-all-contexts** snapshot, including every year/level/stage and all areas/topics, sessions, schedules, blocks and import history; ignores month/area filters after validating supplied references |
| `/api/exports/backup?format=full` | **Deprecated v3: original default context only.** Existing schema/scope name retained for old clients; NOT a complete multi-context school snapshot. A non-default explicit context is rejected; use v4 instead |

Months are 1–12. XLSX preserves the reference workbook's A:J geometry: five weekday code/topic pairs, merged title and metadata, a weekly exact-date row and at least four content rows that extend for overflow. The PDF uses the same five weekday pairs with continuation pages. Neither format hides topics under blocks or automatically moves dates. Saturday/Sunday sessions, school events, schedules, unscheduled items, month/date conflicts and full notes appear in appendices. Monthly selection includes a topic when its exact date **or** assigned month matches, plus topics without a month/date; a conflicting date outside the selected month is shown in the appendix, not relocated into the grid. Only confirmed school events are exported; reference exam/holiday assumptions are not inserted.

XLSX notes are split across appendix rows before Excel's cell-size limit. PDF text wraps and paginates; characters outside the standard PDF font are represented reversibly as `[U+XXXX]`, never silently dropped. Spanish accents are rendered normally.

**Backup scope matters:** the UI labels v2 **Catálogo heredado del plan · v2** and v4 **Toda la escuela · todos los planes · v4**. V2 is available only for PRIMARY grade 6, follows selected area/month filters, excludes custom topics and is accepted by the existing cumulative importer only in a compatible context. The UI explicitly blocks legacy imports into other levels/stages.

V4 has `{format:"shared-school-planner",version:4,scope:"complete-school-all-contexts",schoolName,restoreSupported:false,contexts:[...]}`. Each context entry contains `context` metadata and separate `areas,topics,sessions,schedules,blocks,history` arrays; every row retains its `contextId` and references. The authenticated school is authoritative and all contexts are read in one repeatable-read transaction. Passwords, user/login sessions, credentials and raw import files are excluded. V3 remains a deprecated original-default-only export; its existing version and schema were not silently expanded.

**V3/V4 are export-only snapshots.** They are not accepted by the v1/v2 importer; automatic full-snapshot restoration and encrypted operational database backups are not implemented. Retain these exports for a deliberate migration/recovery workflow, not as a one-click recovery guarantee.

Dependency references: [Apache POI 5.5.1](https://poi.apache.org/download.html), [POI spreadsheet API](https://poi.apache.org/components/spreadsheet/quick-guide.html), [Apache PDFBox 3.0.8](https://pdfbox.apache.org/download.cgi), [PDFBox setup](https://pdfbox.apache.org/3.0/getting-started.html).

## Import contract

Create `LegacyPlanImporter` with the authoritative catalog of known `course|unit|zeroBasedIndex` IDs, the school year and the grade. Call `preview(json, existingState)` to obtain an immutable merged snapshot, per-map counts and warnings. Preview does not persist anything; a future authenticated service must authorize and save the result.

| Input or behavior | Contract |
|---|---|
| Raw payload | Object containing any subset of `assignments`, `status`, `dates`, `notes` |
| Wrapped payload | `{ "version": 2, "year": 2026, "grade": 6, "state": { ... } }`; metadata may be omitted, but present metadata must match the configured policy; versions 1 and 2 only |
| Maps | Missing maps are empty; present maps must be objects containing string values; `null`, arrays and non-string values are rejected |
| Merge | Incoming keys win independently in each map; omitted keys and maps preserve existing data; explicit empty strings are retained, never treated as missing |
| Months | `Marzo` through `Diciembre`; `Septiembre` becomes `Setiembre`; empty string is preserved |
| Status | Empty string means pending; `Trabajado` means completed; `Pendiente` is a legacy display label, not a stored status |
| Dates | Empty string or strict valid `yyyy-MM-dd` in the configured school year; exact dates are never moved to match assignments |
| Notes | Text preserved exactly, including whitespace and empty strings |
| Unknown IDs or fields | Entire preview rejected with structured path/code violations; no partial result or silently discarded topic |
| Ambiguous JSON | Duplicate keys and trailing JSON values rejected |
| Safety limits | At most 2,000,000 input characters, 100,000 characters per string, nesting depth 16 and number length 16; rejected rather than truncated |

Counts describe **incoming fields**, not a replacement plan: `incoming = added + updated + unchanged`. `explicitEmpty` counts incoming empty-string values, and `affectedTopics` is the union of incoming topic IDs across all four maps. Warnings describe the resulting snapshot: `MONTH_DATE_CONFLICT` preserves the exact date, and `UNASSIGNED_TOPIC_DATA` preserves independent data without an assigned month.

## Provenance and scope

- The foundation was generated with Spring Initializr for Java 21. Its generated `4.1.1.RELEASE` parent did not exist on Maven Central; the published version `4.1.1` is used instead.
- Jackson 2 remains explicit for the pure compatibility boundary, catalog loading and internal import adapters. HTTP uses Spring Boot's default JSON stack.
- Tests use synthetic accounts and backups only. The bundled `catalog/legacy-grade6.json` contains only the public `DATA` topic catalog derived read-only from the original HTML, preserving its IDs; no teacher backup or browser state was copied. Original HTML and all synced `sources/` remain untouched.
- Repository publication excludes local runtime databases, synced references, credentials and generated outputs. Synthetic automated test code/fixtures are verification sources only; they are never production seed accounts or runtime teacher data.

## Next step

Select and authorize durable PostgreSQL, then verify HTTPS/proxy deployment, rate limits and backup/restore operations in the authorized environment. H2 integration tests, a frontend build and deployment configuration do not prove production readiness. No real teacher-backup migration was performed.
