# RiichiNexus Backend Remaining TODO

Checked against the current codebase on 2026-03-16.

This file only lists areas that still feel "framework-like", partially wired, or operationally shallow.
Recently completed features such as guest sessions, club applications, club honors/titles, rank privileges, swiss/knockout config consumption, seat state, and RoundRobin/Custom scheduling are intentionally omitted here.

## 1. Business Logic Still Not Fully Landed

- [ ] Deepen dictionary namespace governance beyond the new ownership/review workflow.
  - Current state:
    - metadata keys now require an approved namespace registration before they can be written
    - namespace owners, co-owners, and editors can write keys under their approved prefix through the existing dictionary upsert endpoint
    - super admins can now request/review/transfer/revoke namespaces, update collaborators, and process reminder/escalation batches; `/dictionary/namespaces` supports owner/requester/reviewer filters plus overdue triage
    - pending namespace requests now carry `reviewDueAt`, and `/dictionary/namespaces/backlog` plus `/dictionary/namespaces/reminders/process` expose pending / overdue / due-soon follow-up mechanics
    - namespace ownership can now be assigned only to existing `Active` players; suspended/banned players are rejected for request-on-behalf and transfer flows
    - approved namespaces can now carry an explicit `contextClubId`, and owner/co-owner/editor membership plus metadata writes are validated against that declared club context instead of being inferred heuristically
    - reserved runtime namespaces still remain governed only by `GlobalDictionaryRegistry`
  - Evidence:
    - `src/main/scala/riichinexus/domain/model/Dictionary.scala` now defines collaborator slots and reminder action models alongside approval, transfer, and revocation transitions
    - `src/main/scala/riichinexus/application/service/Services.scala` now computes namespace backlog summaries, processes reminder/escalation batches, enforces active-owner checks plus collaborator write access, and validates explicit context-club membership for namespace governance
    - `src/main/scala/riichinexus/api/ApiServer.scala` now serves collaborator updates, context-club updates, reminder processing, backlog views, and overdue/context filters for dictionary namespaces
  - Suggested completion:
    - if the project later grows beyond club-scoped governance, extract namespace context from `ClubId` into a first-class team/org entity instead of overloading club membership further

- [ ] Deepen the new Advanced Stats Board into fully rules-faithful mahjong analytics.
  - Current state:
    - `Dashboard` and `AdvancedStatsBoard` are now separated
    - advanced metrics now flow through a dedicated repository/API surface with versioned calculator metadata
    - recomputation is now persisted as `AdvancedStatsRecomputeTask` outbox records and drained automatically in the background
    - exact tile-aware ukeire / riichi-pressure defense calculations now continue through meld/open-hand states when paifu actions provide `handTilesAfterAction` and `revealedTiles`
    - the async pipeline now supports delayed retries, `DeadLetter` terminal handling, queue summary metrics, and `missing` / `stale` backfill orchestration through the admin API
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` now contains `AdvancedStatsPipelineService`, async drain scheduling, retry/dead-letter handling, backfill orchestration, queue summaries, and meld-aware exact calculation branches
    - `src/main/scala/riichinexus/domain/model/Paifu.scala` now carries hand snapshots / revealed-tile action metadata
    - exact analytics still degrade to fallback mode when paifu lacks enough detail to reconstruct concealed-hand transitions with confidence
  - Suggested completion:
    - backfill richer paifu exports so historical data also benefits from meld-aware exact mode
    - add operator-facing dead-letter replay / ack flows and long-running batch progress tracking for very large historical backfills

- [ ] Deepen tournament settlement beyond the current ranking-based prize split.
  - Current state:
    - settlement now supports `Draft` / `Finalized` / `Superseded` status, revision history, explicit finalization, house-fee deductions, per-player positive/negative adjustments, and club-share allocation
    - `/tournaments/:id/settle` can now create draft or finalized revisions, and `/tournaments/:id/settlements/:settlementId/finalize` can promote a draft settlement later
    - settlement history queries now preserve superseded revisions instead of overwriting the latest stage snapshot in place
  - Evidence:
    - `src/main/scala/riichinexus/domain/model/Competition.scala` now defines settlement status, adjustment, revision, fee, and club-share fields
    - `src/main/scala/riichinexus/application/service/Services.scala` now supersedes prior revisions, computes net/base/adjusted awards, and exposes `finalizeTournamentSettlement`
    - `src/main/scala/riichinexus/api/ApiServer.scala` now supports settlement status filters and explicit finalize operations
  - Suggested completion:
    - if side pots or tax jurisdictions become real requirements, split generic `adjustments` into typed accounting buckets instead of a single free-form adjustment list
    - expose export-ready club payout aggregates if downstream finance systems need per-club remittance ledgers

- [ ] Enrich appeal handling into a real workflow, not just a verdict log plus table mutation.
  - Current state:
    - appeals now support triage metadata (`priority`, `assigneeId`, `dueAt`), workflow updates, overdue queue filters, and reopen flow through both service and HTTP API
    - moderation cascade records now capture workflow updates and reopened tickets in addition to filed/resolved notifications
    - attachments are still references only, and adjudication side effects still collapse down to the coarse table-resolution enum
  - Evidence:
    - `src/main/scala/riichinexus/domain/model/Competition.scala` now defines appeal priority/assignee/dueAt/reopen state on `AppealTicket`
    - `src/main/scala/riichinexus/application/service/Services.scala` now exposes `updateAppealWorkflow` and `reopenAppeal`, publishes workflow/reopen domain events, and feeds moderation cascade records
    - `src/main/scala/riichinexus/api/ApiServer.scala` now supports `/appeals/:id/workflow`, `/appeals/:id/reopen`, and assignee/priority/overdue filters on `/appeals`
  - Suggested completion:
    - Add evidence storage / checksum / retention lifecycle instead of raw attachment references
    - Model adjudication side effects more precisely than `ForceReset` / `RestorePriorState`
    - If counter-appeals become real, model parent/child appeal chains instead of overloading reopen history

- [ ] Turn club relations into meaningful game logic instead of metadata only.
  - Current state: alliance/rivalry/neutral is stored, mirrored, and shown publicly, but nothing else in scheduling, matchmaking, or analytics consumes it.
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` relation update flow
    - `src/main/scala/riichinexus/application/service/Services.scala` public club directory returns `relations`
    - no downstream consumer outside query/display
  - Suggested completion:
    - Define concrete system effects for alliance/rivalry
    - Use relation edges in scheduling constraints, standings overlays, or cross-club analytics

- [ ] Finish the club-rank privilege system as a registry-driven capability model.
  - Current state: rank privileges now work, but only three strings are interpreted and their meaning is hard-coded in service methods.
  - Evidence:
    - `src/main/scala/riichinexus/domain/model/Core.scala` stores arbitrary privilege strings
    - `src/main/scala/riichinexus/application/service/Services.scala` hard-codes `approve-roster`, `manage-bank`, and `priority-lineup`
  - Suggested completion:
    - Add a privilege registry / enum layer
    - Validate configured privilege codes on rank-tree updates
    - Expose supported privilege semantics through API/docs
    - Decide whether delegated privileges should map into the main authorization service

- [ ] Harden guest-session behavior into a real anonymous access model.
  - Current state: guest sessions are created and persisted, but they have no expiry, revocation, anti-abuse controls, or account-link migration.
  - Evidence:
    - `src/main/scala/riichinexus/domain/model/Core.scala` `GuestAccessSession`
    - `src/main/scala/riichinexus/application/service/Services.scala` `GuestSessionApplicationService`
    - `src/main/scala/riichinexus/api/ApiServer.scala` guest principal resolution uses raw session id as bearer identity
  - Suggested completion:
    - Add TTL, revocation, last-seen metadata, optional IP/device fingerprinting, and upgrade-to-player flow

## 2. Eventing / Projection / Event-Sourcing Gaps

- [ ] Deepen the new event-cascade subscribers beyond the current inbox/projection-repair baseline.
  - Current state: non-match domain events now do trigger downstream work through `EventCascadeProjectionSubscriber`.
  - Evidence:
    - `AppealTicketFiled` / `AppealTicketResolved` now create moderation-inbox cascade records
    - `AppealTicketAdjudicated` now creates notification cascade records
    - `TournamentSettlementRecorded` now creates settlement-export cascade records
    - `GlobalDictionaryUpdated`, `PlayerBanned`, and `ClubDissolved` now create projection-repair cascade records, with immediate club/dashboard/advanced-stats repair where applicable
  - Suggested completion:
    - turn cascade records into richer delivery targets instead of summary-only records
    - connect moderation inbox entries to assignees/SLA queues
    - connect settlement-export entries to real export sinks/webhooks

- [x] Replace synchronous in-process event dispatch with a durable async pipeline.
  - Current state:
    - domain events are now written to a durable outbox before delivery
    - background draining, retry scheduling, and dead-letter handling are now built into the default event bus
    - both in-memory and PostgreSQL modes persist the outbox queue through a dedicated repository/table
  - Evidence:
    - `src/main/scala/riichinexus/domain/model/Operations.scala` defines `DomainEventOutboxRecord`
    - `src/main/scala/riichinexus/infrastructure/events/OutboxEventBus.scala` provides async draining plus retry/dead-letter logic
    - `src/main/scala/riichinexus/infrastructure/postgres/PostgresRepositories.scala` now provisions `domain_event_outbox`
  - Follow-up:
    - add explicit idempotency keys / subscriber delivery cursors if multi-process consumers become a real deployment mode

- [x] Remove duplicated projection formulas and centralize them.
  - Current state:
    - `ProjectionSupport` is now the single entry for club power recalculation and club dashboard aggregation
    - `ClubProjectionSubscriber` and `DashboardProjectionSubscriber` both delegate to the shared projection helpers instead of maintaining copy-pasted formulas
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` top-level `ProjectionSupport`
    - `src/main/scala/riichinexus/application/service/Services.scala` `ClubProjectionSubscriber` now reuses `ProjectionSupport.recalculateClubPowerRating`
    - `src/main/scala/riichinexus/application/service/Services.scala` `DashboardProjectionSubscriber` now reuses `ProjectionSupport.buildClubDashboard`
  - Follow-up:
    - Optionally drive club projection coefficients from the global dictionary if operators need runtime tuning beyond the existing power-rating weights

## 3. Infrastructure / Operational Depth Still Missing

- [x] Add stronger transactional guarantees and consistency protection.
  - Current state:
    - major mutable aggregates now carry `version`
    - in-memory and PostgreSQL repositories now enforce compare-and-swap semantics on save instead of silently overwriting stale state
    - concurrent write conflicts now raise a dedicated `OptimisticConcurrencyException`
  - Evidence:
    - `src/main/scala/riichinexus/application/ports/TransactionManager.scala`
    - `src/main/scala/riichinexus/domain/model/Core.scala`, `Competition.scala`, `Dictionary.scala`, `Dashboard.scala`, and `Operations.scala` now persist `version`
    - `src/main/scala/riichinexus/infrastructure/memory/InMemoryAdapters.scala` and `src/main/scala/riichinexus/infrastructure/postgres/PostgresRepositories.scala` now perform optimistic CAS checks
  - Follow-up:
    - keep retry policy explicit at service boundaries for operations that are safe to replay, instead of blindly retrying every transaction block

- [ ] Give public read models richer schedule and club views.
  - Current state: public schedule view is shallow and only exposes tournament window plus current table count; club directory is also summary-only.
  - Evidence:
    - `src/main/scala/riichinexus/domain/model/Dashboard.scala` `PublicScheduleView` / `PublicClubDirectoryEntry`
    - `src/main/scala/riichinexus/application/service/Services.scala` `PublicQueryService`
  - Suggested completion:
    - Include round number, stage-specific timing, active-table details, whitelist/registration status, and public-safe club metadata

- [ ] Add storage/validation policy for appeal attachments.
  - Current state: attachments are assumed to be valid references; there is no storage adapter, checksum, media policy, or retention flow.
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` `fileAppeal`
  - Suggested completion:
    - Define upload backend, signed URLs, media validation, retention rules, and attachment audit trails

- [ ] Revisit the current club power-rating formula.
  - Current state: club power is essentially `average member elo + totalPoints / 1000`, which is enough for a placeholder leaderboard but still too simple for long-running esports operations.
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` `ProjectionSupport.recalculateClubPowerRating`
    - `src/main/scala/riichinexus/application/service/Services.scala` `ClubProjectionSubscriber`
  - Suggested completion:
    - Fold in weighted recency, roster depth, stage strength, club-vs-club results, and dictionary-driven coefficients

## 4. Recommended Execution Order

- [ ] First pass:
  - typed dictionary registry / schema hardening
  - event subscribers for non-match events
  - advanced stats board separation

- [ ] Second pass:
  - appeal workflow deepening
  - tournament settlement expansion
  - privilege registry and validation

- [ ] Third pass:
  - guest-session hardening
  - durable event bus / outbox
  - optimistic locking / concurrency protection
