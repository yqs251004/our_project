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


Disable Swiss carry-over so each new round ranking only reflects the latest archived round:

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/rules   -H "Content-Type: application/json"   -d '{
    "operatorId": "player-tournament-admin",
    "advancementRuleType": "SwissCut",
    "cutSize": 8,
    "carryOverPoints": false,
    "maxRounds": 2,
    "schedulingPoolSize": 2
  }'
```

```bash
curl "http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/standings"
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
      { "code": "elite", "label": "Elite", "minimumContribution": 1500, "privileges": ["priority-lineup"] }
    ],
    "note": "season refresh"
  }'
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

Audit aggregate lookup with RBAC-scoped operator:

```bash
curl "http://localhost:8080/audits/dictionary/rank.formula?operatorId=player-super-admin&eventType=GlobalDictionaryUpserted&limit=20"
```

Audit collection lookup:

```bash
curl "http://localhost:8080/audits?operatorId=player-super-admin&aggregateType=dictionary&actorId=player-super-admin&limit=20"
```

## 7. Dashboard reads now require `operatorId`

```bash
curl "http://localhost:8080/dashboards/players/player-123?operatorId=player-123"
```

```bash
curl "http://localhost:8080/dashboards/clubs/club-123?operatorId=player-club-admin"
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
            { "sequenceNo": 2, "actor": "player-a", "actionType": "Discard", "tile": "9p", "shantenAfterAction": 2, "note": null },
            { "sequenceNo": 3, "actor": "player-b", "actionType": "Riichi", "tile": null, "shantenAfterAction": 0, "note": "closed riichi" },
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
