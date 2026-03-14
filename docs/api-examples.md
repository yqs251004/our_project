# RiichiNexus API Examples

These examples assume the local server is running at `http://localhost:8080`.

## 1. Preview a knockout bracket

```bash
curl http://localhost:8080/tournaments/tournament-123/stages/stage-finals/bracket
```

## 2. Preview stage standings and advancement

```bash
curl http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/standings
```

```bash
curl http://localhost:8080/tournaments/tournament-123/stages/stage-swiss-1/advancement
```

## 3. Upload a paifu for a completed table

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

## 4. Adjudicate an appeal

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

## 5. Complete a stage after all tables are archived

```bash
curl -X POST http://localhost:8080/tournaments/tournament-123/stages/stage-finals/complete \
  -H "Content-Type: application/json" \
  -d '{
    "operatorId": "player-tournament-admin"
  }'
```
