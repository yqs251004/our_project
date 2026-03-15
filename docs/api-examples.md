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

## 4. Query tournament tables, records and appeals

```bash
curl "http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/tables?status=WaitingPreparation&playerId=player-123&limit=8"
```

Filter a specific scheduled round from a multi-round stage:

```bash
curl "http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/tables?roundNumber=2&limit=8"
```

```bash
curl "http://localhost:8080/records?tournamentId=tournament-123&stageId=stage-swiss-1&playerId=player-123&limit=20"
```

```bash
curl "http://localhost:8080/appeals?tournamentId=tournament-123&status=Open&tableId=table-123&limit=20"
```

## 5. Club treasury, point pool and rank tree operations

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

## 6. Query dictionary and audit trail

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
