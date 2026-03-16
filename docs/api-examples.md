# RiichiNexus API Examples

These examples assume the local server is running at `http://localhost:8080`.

## List Query Convention

All collection endpoints now return a shared envelope:

```json
{
  "items": [],
  "total": 0,
  "limit": 20,
  "offset": 0,
  "hasMore": false,
  "appliedFilters": {}
}
```

Shared query parameters:
- `limit`: positive integer, default `20`, capped at `100`
- `offset`: zero-based starting index, default `0`
- endpoint-specific filters appear in `appliedFilters`

## 1. Public schedules with status filters and pagination

```bash
curl "http://localhost:8080/public/schedules?tournamentStatus=InProgress&stageStatus=Active&limit=10&offset=0"
```

## 2. Public player leaderboard filtered by club

```bash
curl "http://localhost:8080/public/leaderboards/players?clubId=club-123&status=Active&limit=20"
```

The public player leaderboard now carries both the raw `currentRank` snapshot and a dictionary-backed `normalizedRankScore`, so mixed-platform ladders can break ELO ties consistently.

## 3. Query players and clubs with shared pagination

```bash
curl "http://localhost:8080/players?clubId=club-123&status=Active&nickname=alice&limit=10&offset=0"
```

```bash
curl "http://localhost:8080/clubs?activeOnly=true&memberId=player-123&limit=10"
```

## 4. Create a guest session and submit a club application anonymously

Create a reusable guest session for an unauthenticated visitor:

```bash
curl -X POST http://localhost:8080/guest-sessions   -H "Content-Type: application/json"   -d '{
    "displayName": "AnonymousFan"
  }'
```

Use that session as the anonymous identity when submitting a club application:

```bash
curl -X POST http://localhost:8080/clubs/club-123/applications   -H "Content-Type: application/json"   -d '{
    "guestSessionId": "guest-123",
    "displayName": "ignored-when-session-exists",
    "message": "I would like to join as a visitor first"
  }'
```

Query the created guest session later:

```bash
curl http://localhost:8080/guest-sessions/guest-123
```

Registered players can also submit an application under their own identity by providing `operatorId`; the backend will derive `applicantUserId` and display name automatically:

```bash
curl -X POST http://localhost:8080/clubs/club-123/applications   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-registered-1",
    "displayName": "ignored-when-operator-exists",
    "message": "I'd like to join next split"
  }'
```

Withdraw a still-pending club application as the original guest session or registered player:

```bash
curl -X POST http://localhost:8080/clubs/club-123/applications/membership-123/withdraw   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-registered-1",
    "note": "schedule changed"
  }'
```

## 5. Query tournament tables, records and appeals

```bash
curl "http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/tables?status=WaitingPreparation&playerId=player-123&limit=8"
```

Filter a specific scheduled round from a multi-round stage:

```bash
curl "http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/tables?roundNumber=2&limit=8"
```


Reserve seats can be declared in a stage lineup; scheduling promotes them when active seats become unavailable, and `maxRounds` caps how many rounds are materialized for the stage:

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/lineups \
  -H "Content-Type: application/json" \
  -d '{
    "clubId": "club-123",
    "operatorId": "player-club-admin",
    "seats": [
      { "playerId": "player-a", "preferredWind": "East" },
      { "playerId": "player-b", "preferredWind": "South" },
      { "playerId": "player-c", "preferredWind": "West" },
      { "playerId": "player-r1", "reserve": true }
    ],
    "note": "reserve enabled lineup"
  }'
```


Swiss stages now also consume `pairingMethod`; for example, `snake` produces serpentine groupings instead of the default `balanced-elo`:

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/rules   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-tournament-admin",
    "advancementRuleType": "SwissCut",
    "cutSize": 8,
    "pairingMethod": "snake",
    "carryOverPoints": false,
    "maxRounds": 2,
    "schedulingPoolSize": 2
  }'
```

```bash
curl "http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/standings"
```

Stage rules can also be injected from a global dictionary template. First register a template as a super admin:

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "key": "tournament.rule-template.swiss-snake-template",
    "value": "advancement=SwissCut;cutSize=8;pairingMethod=snake;maxRounds=2;schedulingPoolSize=2;note=template backed",
    "note": "shared swiss template"
  }'
```

Then reference it when configuring a stage without repeating the full rule payload:

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/rules   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-tournament-admin",
    "ruleTemplateKey": "swiss-snake-template"
  }'
```

Round-robin stages now use dedicated pod rotation across rounds instead of reusing Swiss scheduling semantics, and custom stages can cap each round to a featured subset of tables with `targetTableCount`:

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-roundrobin-1/rules   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-tournament-admin",
    "advancementRuleType": "ScoreThreshold",
    "thresholdScore": 0,
    "schedulingPoolSize": 2
  }'
```

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-custom-1/rules   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-tournament-admin",
    "advancementRuleType": "Custom",
    "targetTableCount": 1,
    "note": "top=4"
  }'
```

Knockout stages now consume `seedingPolicy` for visible bracket ordering. `ranking` keeps qualified players in standings order, while `rating` reorders them by ELO before the bracket is seeded:

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-finals/rules   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-tournament-admin",
    "advancementRuleType": "KnockoutElimination",
    "targetTableCount": 2,
    "bracketSize": 8,
    "seedingPolicy": "rating",
    "thirdPlaceMatch": true
  }'
```

```bash
curl "http://localhost:8080/tournaments/tournament-123/stages/stage-finals/bracket"
```

Paifu round settlements are now validated and preserved into archived match-record notes:

```json
{
  "riichiSticksDelta": 1000,
  "honbaPayment": 300,
  "notes": ["riichi sticks awarded"]
}
```

Players can update their own table seat readiness or disconnect state before a table starts:

```bash
curl -X POST http://localhost:8080/tables/table-123/seats/East/state \
  -H "Content-Type: application/json" \
  -d '{
    "operatorId": "player-a",
    "ready": true
  }'
```

```bash
curl -X POST http://localhost:8080/tables/table-123/seats/South/state \
  -H "Content-Type: application/json" \
  -d '{
    "operatorId": "player-b",
    "disconnected": true,
    "note": "temporary network loss"
  }'
```

```bash
curl "http://localhost:8080/records?tournamentId=tournament-123&stageId=stage-swiss-1&playerId=player-123&limit=20"
```

```bash
curl "http://localhost:8080/appeals?tournamentId=tournament-123&status=Open&tableId=table-123&limit=20"
```

## 6. Club treasury, point pool and rank tree operations

Assign an internal club title:

```bash
curl -X POST http://localhost:8080/clubs/club-123/titles   -H "Content-Type: application/json"   -d '{
    "playerId": "player-member-1",
    "operatorId": "player-club-admin",
    "title": "Vice Captain",
    "note": "spring roster"
  }'
```

Clear an assigned club title:

```bash
curl -X POST http://localhost:8080/clubs/club-123/titles/player-member-1/clear   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "note": "rotation"
  }'
```


Adjust a club treasury balance:

```bash
curl -X POST http://localhost:8080/clubs/club-123/treasury   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "delta": 5000,
    "note": "sponsor settlement"
  }'
```

Adjust a club point pool:

```bash
curl -X POST http://localhost:8080/clubs/club-123/point-pool   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "delta": 320,
    "note": "internal ladder reward"
  }'
```

Replace the full club rank tree:

```bash
curl -X POST http://localhost:8080/clubs/club-123/rank-tree   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "ranks": [
      { "code": "rookie", "label": "Rookie", "minimumContribution": 0, "privileges": [] },
      { "code": "elite", "label": "Elite", "minimumContribution": 1500, "privileges": ["priority-lineup"] },
      { "code": "officer", "label": "Officer", "minimumContribution": 2500, "privileges": ["approve-roster", "manage-bank"] }
    ],
    "note": "season refresh"
  }'
```

Adjust one member's club contribution so the rank tree starts granting effective privileges:

```bash
curl -X POST http://localhost:8080/clubs/club-123/member-contributions   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "playerId": "player-member-1",
    "delta": 2600,
    "note": "season operations contribution"
  }'
```

Inspect resolved member privileges after contribution and rank promotion:

```bash
curl http://localhost:8080/clubs/club-123/member-privileges/player-member-1
```

Or list only members who currently hold a delegated capability such as `manage-bank`:

```bash
curl "http://localhost:8080/clubs/club-123/member-privileges?privilege=manage-bank"
```

Award or update a club honor:

```bash
curl -X POST http://localhost:8080/clubs/club-123/honors   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "title": "Spring Split Champion",
    "note": "won grand finals",
    "achievedAt": "2026-03-15T18:00:00Z"
  }'
```

Revoke a club honor:

```bash
curl -X POST http://localhost:8080/clubs/club-123/honors/revoke   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "title": "Spring Split Champion",
    "note": "season rollover"
  }'
```

Set or clear a reciprocal club relation:

```bash
curl -X POST http://localhost:8080/clubs/club-123/relations   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-club-admin",
    "targetClubId": "club-456",
    "relation": "Alliance",
    "note": "shared training block"
  }'
```

## 7. Query dictionary and audit trail

Single dictionary key lookup:

```bash
curl http://localhost:8080/dictionary/rank.formula
```

Paginated dictionary listing:

```bash
curl "http://localhost:8080/dictionary?prefix=rank.&limit=20"
```

Inspect the typed runtime dictionary schema and the current unknown-key policy:

```bash
curl "http://localhost:8080/dictionary/schema"
```

Unknown keys outside reserved runtime namespaces are now treated as governed metadata families: they can be stored only after a namespace has been requested and approved. Unregistered keys inside reserved namespaces such as `rating.`, `club.power.`, `settlement.`, `rank.normalization.`, and `tournament.rule-template.` are still rejected until they are explicitly added to the registry.

Request and review a metadata namespace before non-admin owners write free-form dictionary families:

```bash
curl -X POST http://localhost:8080/dictionary/namespaces   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-product-owner",
    "namespacePrefix": "ui.banner",
    "contextClubId": "club-content-ops",
    "coOwnerPlayerIds": ["player-content-lead"],
    "editorPlayerIds": ["player-copywriter"],
    "note": "frontend copy family",
    "reviewDueAt": "2026-03-20T12:00:00Z"
  }'
```

```bash
curl -X POST http://localhost:8080/dictionary/namespaces/review   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "namespacePrefix": "ui.banner",
    "approve": true,
    "note": "approved metadata family"
  }'
```

Inspect namespace ownership and review status:

```bash
curl "http://localhost:8080/dictionary/namespaces?operatorId=player-super-admin&status=Approved"
```

Filter approved namespaces by their explicit club context:

```bash
curl "http://localhost:8080/dictionary/namespaces?operatorId=player-super-admin&status=Approved&contextClubId=club-content-ops"
```

Inspect namespace backlog and overdue pending reviews:

```bash
curl "http://localhost:8080/dictionary/namespaces/backlog?operatorId=player-super-admin&asOf=2026-03-19T12:00:00Z&dueSoonHours=24"
```

```bash
curl "http://localhost:8080/dictionary/namespaces?operatorId=player-super-admin&status=Pending&overdueOnly=true&asOf=2026-03-19T12:00:00Z"
```

Transfer an approved metadata namespace to a new owner:

```bash
curl -X POST http://localhost:8080/dictionary/namespaces/transfer   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "namespacePrefix": "ui.banner",
    "newOwnerPlayerId": "player-content-ops",
    "note": "handoff to content ops"
  }'
```

Revoke a namespace when the family is retired or ownership should be frozen:

```bash
curl -X POST http://localhost:8080/dictionary/namespaces/revoke   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "namespacePrefix": "ui.banner",
    "note": "retired metadata family"
  }'
```

Update co-owners and editors after approval:

```bash
curl -X POST http://localhost:8080/dictionary/namespaces/collaborators   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-content-lead",
    "namespacePrefix": "ui.banner",
    "coOwnerPlayerIds": ["player-content-lead"],
    "editorPlayerIds": ["player-copywriter-2"],
    "note": "rotate banner editor"
  }'
```

Update or clear the explicit club context after approval:

```bash
curl -X POST http://localhost:8080/dictionary/namespaces/context   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-product-owner",
    "namespacePrefix": "ui.banner",
    "contextClubId": null,
    "note": "detach from current club ownership"
  }'
```

Process due-soon / overdue namespace reminders:

```bash
curl -X POST http://localhost:8080/dictionary/namespaces/reminders/process   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "asOf": "2026-03-19T12:00:00Z",
    "dueSoonHours": 24,
    "reminderIntervalHours": 12,
    "escalationGraceHours": 72
  }'
```

Once approved, the namespace owner can update metadata keys under that prefix through the same dictionary write endpoint. Namespace owners must be existing `Active` players; suspended or banned players cannot receive new namespace ownership through request-on-behalf or transfer flows. When `contextClubId` is set, owners/co-owners/editors and runtime writers must all currently belong to that exact club.

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-product-owner",
    "key": "ui.banner.message",
    "value": "Spring finals this weekend",
    "note": "owned metadata write"
  }'
```

Runtime-sensitive dictionary keys are now consumed by live services. The currently wired keys are:

- `rating.elo.kFactor`
- `rating.elo.placementWeight`
- `rating.elo.scoreWeight`
- `rating.elo.umaWeight`
- `club.power.eloWeight`
- `club.power.pointWeight`
- `club.power.baseBonus`
- `settlement.defaultPayoutRatios`
- `rank.normalization.<platform>.<tier>`
- `rank.normalization.<platform>.<tier>.<stars>`
- `rank.normalization.<platform>.<tier>-<stars>`
- `rank.normalization.<platform>.starWeight`
- `tournament.rule-template.<templateKey>`

For example, increase the live ELO k-factor without redeploying:

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "key": "rating.elo.kFactor",
    "value": "48",
    "note": "mid-season volatility bump"
  }'
```

Tune the club power formula with a flat bonus:

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "key": "club.power.baseBonus",
    "value": "25",
    "note": "founder prestige bonus"
  }'
```

Or set default tournament payout ratios that will be used when `/tournaments/:id/settle` omits `payoutRatios`:

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "key": "settlement.defaultPayoutRatios",
    "value": "0.6,0.25,0.15",
    "note": "major event payout profile"
  }'
```

Normalize cross-platform ranks for the public leaderboard:

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "key": "rank.normalization.tenhou.5-dan",
    "value": "550",
    "note": "Tenhou dan baseline"
  }'
```

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "key": "rank.normalization.mahjongsoul.master",
    "value": "600",
    "note": "Mahjong Soul master baseline"
  }'
```

```bash
curl -X POST http://localhost:8080/admin/dictionary   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "key": "rank.normalization.mahjongsoul.starWeight",
    "value": "10",
    "note": "Mahjong Soul star increment"
  }'
```

Audit aggregate lookup with RBAC-scoped operator:

```bash
curl "http://localhost:8080/audits/dictionary/rank.formula?operatorId=player-super-admin&eventType=GlobalDictionaryUpserted&limit=20"
```

Audit collection lookup:

```bash
curl "http://localhost:8080/audits?operatorId=player-super-admin&aggregateType=dictionary&actorId=player-super-admin&limit=20"
```

Inspect non-match event cascade records such as moderation inbox entries, settlement export summaries, and projection repairs:

```bash
curl "http://localhost:8080/admin/event-cascade-records?operatorId=player-super-admin&eventType=PlayerBanned"
```

```bash
curl "http://localhost:8080/admin/event-cascade-records?operatorId=player-super-admin&consumer=ModerationInbox&status=Pending"
```

## 7. Dashboard reads now require `operatorId`

The lightweight dashboard endpoints now return summary KPIs only. Advanced metrics such as defense stability, ukeire expectation, shanten progression, and pressure-response rates have been split into dedicated advanced-stats boards.

```bash
curl "http://localhost:8080/dashboards/players/player-123?operatorId=player-123"
```

```bash
curl "http://localhost:8080/dashboards/clubs/club-123?operatorId=player-club-admin"
```

Read the dedicated advanced-stats boards. These responses now include calculation metadata such as `calculatorVersion`, `strictRoundSampleSize`, `exactUkeireSampleRate`, and `exactDefenseSampleRate` so clients can distinguish exact tile-analytic samples from heuristic fallback samples.

```bash
curl "http://localhost:8080/advanced-stats/players/player-123?operatorId=player-123"
```

```bash
curl "http://localhost:8080/advanced-stats/clubs/club-123?operatorId=player-club-admin"
```

Advanced-stats recompute tasks are now outbox-backed and drained automatically in the background. The admin endpoints remain available for observability, manual backfill, and forced replays.

List queued or completed recompute tasks as a super admin:

```bash
curl "http://localhost:8080/admin/advanced-stats/tasks?operatorId=player-super-admin&status=Pending"
```

Read the queue summary, including runnable pending work, delayed retries, and dead-letter totals:

```bash
curl "http://localhost:8080/admin/advanced-stats/summary?operatorId=player-super-admin"
```

Queue a targeted recompute:

```bash
curl -X POST http://localhost:8080/admin/advanced-stats/recompute   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "ownerType": "player",
    "ownerId": "player-123",
    "reason": "calculator-v2-backfill"
  }'
```

Queue a bulk backfill for owners whose advanced-stats boards are still missing:

```bash
curl -X POST http://localhost:8080/admin/advanced-stats/recompute   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "mode": "Missing",
    "reason": "historical-missing-board-backfill",
    "limit": 500
  }'
```

Queue a bulk backfill for stale calculator versions:

```bash
curl -X POST http://localhost:8080/admin/advanced-stats/recompute   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "mode": "Stale",
    "reason": "calculator-version-upgrade",
    "limit": 500
  }'
```

Process the queued recompute worker batch:

```bash
curl -X POST http://localhost:8080/admin/advanced-stats/process   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-super-admin",
    "limit": 50
  }'
```

## 8. Upload a paifu for a completed table

```bash
curl -X POST http://localhost:8080/tables/table-123/paifu \
  -H "Content-Type: application/json" \
  -d '{
    "operatorId": "player-admin-1",
    "paifu": {
      "id": "paifu-demo-1",
      "metadata": {
        "recordedAt": "2026-03-14T12:00:00Z",
        "source": "obs-import",
        "tableId": "table-123",
        "tournamentId": "tournament-123",
        "stageId": "stage-finals",
        "seats": [
          { "seat": "East", "playerId": "player-a", "initialPoints": 25000, "disconnected": false, "ready": true, "clubId": "club-east" },
          { "seat": "South", "playerId": "player-b", "initialPoints": 25000, "disconnected": false, "ready": true, "clubId": "club-south" },
          { "seat": "West", "playerId": "player-c", "initialPoints": 25000, "disconnected": false, "ready": true, "clubId": "club-west" },
          { "seat": "North", "playerId": "player-d", "initialPoints": 25000, "disconnected": false, "ready": true, "clubId": "club-north" }
        ],
        "matchRecordId": null
      },
      "rounds": [
        {
          "descriptor": { "roundWind": "East", "handNumber": 1, "honba": 0 },
          "initialHands": {
            "player-a": ["1m", "2m", "3m"],
            "player-b": ["4m", "5m", "6m"],
            "player-c": ["7m", "8m", "9m"],
            "player-d": ["1p", "2p", "3p"]
          },
          "actions": [
            { "sequenceNo": 1, "actor": "player-a", "actionType": "Draw", "tile": "4m", "shantenAfterAction": 3, "note": null },
            { "sequenceNo": 2, "actor": "player-a", "actionType": "Discard", "tile": "9p", "shantenAfterAction": 2, "handTilesAfterAction": ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "1p", "2p", "3p", "4p", "5p", "6p"], "revealedTiles": ["9p"], "note": null },
            { "sequenceNo": 3, "actor": "player-b", "actionType": "Riichi", "tile": null, "shantenAfterAction": 0, "handTilesAfterAction": null, "revealedTiles": ["5s"], "note": "closed riichi" },
            { "sequenceNo": 4, "actor": "player-b", "actionType": "Win", "tile": "5s", "shantenAfterAction": -1, "note": null }
          ],
          "result": {
            "outcome": "Ron",
            "winner": "player-b",
            "target": "player-c",
            "han": 3,
            "fu": 40,
            "yaku": [
              { "name": "Riichi", "han": 1 },
              { "name": "Pinfu", "han": 1 },
              { "name": "Ippatsu", "han": 1 }
            ],
            "points": 7700,
            "scoreChanges": [
              { "playerId": "player-a", "delta": 0 },
              { "playerId": "player-b", "delta": 7700 },
              { "playerId": "player-c", "delta": -7700 },
              { "playerId": "player-d", "delta": 0 }
            ],
            "settlement": null
          }
        }
      ],
      "finalStandings": [
        { "playerId": "player-b", "seat": "South", "finalPoints": 32700, "placement": 1, "uma": 0, "oka": 0 },
        { "playerId": "player-a", "seat": "East", "finalPoints": 25000, "placement": 2, "uma": 0, "oka": 0 },
        { "playerId": "player-d", "seat": "North", "finalPoints": 25000, "placement": 3, "uma": 0, "oka": 0 },
        { "playerId": "player-c", "seat": "West", "finalPoints": 17300, "placement": 4, "uma": 0, "oka": 0 }
      ]
    }
  }'
```

Validation notes for uploads:
- `rounds[*].result.scoreChanges` must cover all four seated players and sum to zero.
- `rounds[*].actions` must be sorted by `sequenceNo` and contain exactly one terminal action.
- `finalStandings[*].finalPoints` must equal initial points plus the cumulative deltas from all rounds.

## 9. Adjudicate an appeal

```bash
curl -X POST http://localhost:8080/appeals/appeal-123/adjudicate \
  -H "Content-Type: application/json" \
  -d '{
    "operatorId": "player-tournament-admin",
    "decision": "Resolve",
    "verdict": "table result confirmed after screenshot review",
    "tableResolution": "ArchiveTable",
    "note": "No scoring rollback needed"
  }'
```

Possible values:
- `decision`: `Resolve`, `Reject`, `Escalate`
- `tableResolution`: `RestorePriorState`, `ArchiveTable`, `ResumeScoring`, `ResumePlay`, `ForceReset`

## 10. Complete a stage after all tables are archived

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-finals/complete \
  -H "Content-Type: application/json" \
  -d '{
    "operatorId": "player-tournament-admin"
  }'
```
