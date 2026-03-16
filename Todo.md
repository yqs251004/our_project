# RiichiNexus Backend Remaining TODO

Checked against the current codebase on 2026-03-16.

This file only lists areas that still feel "framework-like", partially wired, or operationally shallow.
Recently completed features such as guest sessions, club applications, club honors/titles, rank privileges, swiss/knockout config consumption, seat state, and RoundRobin/Custom scheduling are intentionally omitted here.

## 1. Business Logic Still Not Fully Landed

- [ ] Formalize the global dictionary into a typed registry now that runtime consumers are wired.
  - Current state:
    - dictionary entries now affect live ELO tuning, club power formula, default tournament payout ratios, public rank normalization, and tournament rule templates
    - the supported keys are still validated by ad-hoc string prefixes rather than a first-class registry/schema
  - Evidence:
    - runtime consumption is now wired in `src/main/scala/riichinexus/application/service/Services.scala`
    - `src/main/scala/riichinexus/domain/service/RatingService.scala` now reads runtime ELO config through a provider
    - `PublicQueryService` and stage-rule normalization now also resolve dictionary-backed rank normalization and rule templates
  - Suggested completion:
    - introduce a typed registry or schema object for supported dictionary keys
    - centralize parsing/validation/error messages instead of scattering prefix-based decoders
    - decide whether unknown keys remain free-form metadata or must move behind explicit registration

- [ ] Deepen the new Advanced Stats Board into fully rules-faithful mahjong analytics.
  - Current state:
    - `Dashboard` and `AdvancedStatsBoard` are now separated
    - advanced metrics now flow through a dedicated repository/API surface with versioned calculator metadata
    - recomputation is now persisted as `AdvancedStatsRecomputeTask` outbox records and drained automatically in the background
    - exact tile-aware ukeire / riichi-pressure defense calculations now continue through meld/open-hand states when paifu actions provide `handTilesAfterAction` and `revealedTiles`
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` now contains `AdvancedStatsPipelineService`, async drain scheduling, and meld-aware exact calculation branches
    - `src/main/scala/riichinexus/domain/model/Paifu.scala` now carries hand snapshots / revealed-tile action metadata
    - exact analytics still degrade to fallback mode when paifu lacks enough detail to reconstruct concealed-hand transitions with confidence
  - Suggested completion:
    - backfill richer paifu exports so historical data also benefits from meld-aware exact mode
    - add retry policy, dead-letter handling, and operational metrics around the async outbox drain
    - add bulk historical recalculation orchestration and observability for large dataset backfills

- [ ] Deepen tournament settlement beyond the current ranking-based prize split.
  - Current state: settlement is a single ranking snapshot plus a simple prize allocator; it does not model club/team splits, side awards, deductions, taxes/fees, or settlement revisions.
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` in `settleTournament`
    - `src/main/scala/riichinexus/application/service/Services.scala` in `allocatePrizePool`
  - Suggested completion:
    - Support configurable payout schemas, club/team settlement, side pots, and revision history
    - Persist settlement status transitions instead of only a final snapshot

- [ ] Enrich appeal handling into a real workflow, not just a verdict log plus table mutation.
  - Current state: appeal attachments are references only, and adjudication is limited to resolve/reject/escalate plus a coarse table resolution.
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` in `AppealApplicationService`
    - no assignee, priority, SLA, reopen flow, counter-appeal, or evidence storage pipeline
  - Suggested completion:
    - Add appeal ownership, queues, deadlines, reopen/escalate chains, and evidence lifecycle
    - Model adjudication side effects more precisely than `ForceReset` / `RestorePriorState`

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

- [ ] Finish the event-driven cascade promised by the architecture description.
  - Current state: only `MatchRecordArchived` has active subscribers; other published domain events do not trigger any downstream processing.
  - Evidence:
    - `src/main/scala/riichinexus/domain/event/DomainEvents.scala`
    - active subscribers in `src/main/scala/riichinexus/application/service/Services.scala`:
      - `RatingProjectionSubscriber`
      - `ClubProjectionSubscriber`
      - `DashboardProjectionSubscriber`
    - events with no subscriber behavior today:
      - `AppealTicketFiled`
      - `AppealTicketResolved`
      - `AppealTicketAdjudicated`
      - `TournamentSettlementRecorded`
      - `GlobalDictionaryUpdated`
      - `PlayerBanned`
      - `ClubDissolved`
  - Suggested completion:
    - Define downstream consumers for notifications, cache refresh, moderation, settlement exports, and projection repair

- [ ] Replace synchronous in-process event dispatch with a durable async pipeline.
  - Current state: the event bus is in-memory and synchronous; failures would happen inline and there is no outbox, retry, replay, or delivery auditing.
  - Evidence:
    - `src/main/scala/riichinexus/application/ports/EventBus.scala`
    - `src/main/scala/riichinexus/infrastructure/memory/InMemoryAdapters.scala` `InMemoryDomainEventBus`
  - Suggested completion:
    - Introduce outbox persistence, background consumers, retries, dead-letter handling, and idempotency keys

- [ ] Remove duplicated projection formulas and centralize them.
  - Current state: club power rating and club dashboard aggregation logic are duplicated in both `ProjectionSupport` and subscriber implementations.
  - Evidence:
    - `src/main/scala/riichinexus/application/service/Services.scala` top-level `ProjectionSupport`
    - `src/main/scala/riichinexus/application/service/Services.scala` `ClubProjectionSubscriber` and `DashboardProjectionSubscriber`
  - Suggested completion:
    - Extract reusable projection calculators
    - Optionally drive formulas from the global dictionary

## 3. Infrastructure / Operational Depth Still Missing

- [ ] Add stronger transactional guarantees and consistency protection.
  - Current state: in-memory mode uses `NoOpTransactionManager`; there is no optimistic locking/version field on aggregates, so concurrent updates can silently overwrite each other.
  - Evidence:
    - `src/main/scala/riichinexus/application/ports/TransactionManager.scala`
    - aggregate models do not carry version numbers
  - Suggested completion:
    - Add aggregate versioning or compare-and-swap semantics
    - Define retry behavior for concurrent writes

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
