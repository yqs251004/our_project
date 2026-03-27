# RiichiNexus Demo Frontend API

This document lists the backend calls the frontend demo is expected to use first.

Base URL examples below assume:

```text
http://localhost:8080
```

## 1. Recommended Starting Point

For most demo pages, start from the all-in-one summary:

```bash
curl "http://localhost:8080/demo/summary?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

This single response already contains:
- seeded players
- seeded clubs
- tournament and table cards
- public schedules
- public club directory
- public player leaderboard
- public club leaderboard
- recommended follow-up requests
- readiness state for derived views

## 2. Supported Demo Variants

Query parameter:

```text
variant=Basic | Leaderboard | Appeal
```

Suggested use:
- `Basic`: safest default for homepage / overview / cards
- `Leaderboard`: best for ranking pages and charts
- `Appeal`: best for moderation / appeal workflow demos

## 3. Core Demo Calls

### 3.1 Summary

```bash
curl "http://localhost:8080/demo/summary?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

### 3.2 Guide

Use this when the frontend wants a ready-made walkthrough order or presenter notes:

```bash
curl "http://localhost:8080/demo/guide?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

### 3.3 Readiness

Use this for loading indicators or to verify projections are ready:

```bash
curl "http://localhost:8080/demo/readiness?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

### 3.4 Bootstrap

Create demo data explicitly:

```bash
curl -X POST "http://localhost:8080/demo/bootstrap?variant=Basic&refreshDerived=true"
```

### 3.5 Refresh

Refresh an existing scenario without changing its overall variant:

```bash
curl -X POST "http://localhost:8080/demo/refresh?variant=Basic&bootstrapIfMissing=true"
```

### 3.6 Reset

Reset a variant back to its seeded baseline:

```bash
curl -X POST "http://localhost:8080/demo/reset?variant=Basic&refreshDerived=true"
```

## 4. Frontend Page Mapping

### 4.1 Landing / Overview Page

Primary call:

```bash
curl "http://localhost:8080/demo/summary?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

Fields typically used:
- `players`
- `clubs`
- `tournament`
- `publicSchedules`
- `playerLeaderboard`
- `clubLeaderboard`

### 4.2 Player Cards / Stats View

Start from summary, then optionally drill down:

```bash
curl "http://localhost:8080/dashboards/players/<playerId>?operatorId=<playerId>"
```

```bash
curl "http://localhost:8080/advanced-stats/players/<playerId>?operatorId=<playerId>"
```

The recommended seeded operator id is returned inside `demo/summary` as:

```text
recommendedOperatorId
```

### 4.3 Club Directory / Club Cards

```bash
curl "http://localhost:8080/public/clubs"
```

Also available directly inside `demo/summary` as:

```text
publicClubDirectory
```

### 4.4 Leaderboard Page

Use the dedicated leaderboard variant first:

```bash
curl "http://localhost:8080/demo/summary?variant=Leaderboard&bootstrapIfMissing=true&refreshDerived=true"
```

Optional direct calls:

```bash
curl "http://localhost:8080/public/leaderboards/players"
```

```bash
curl "http://localhost:8080/public/leaderboards/clubs"
```

### 4.5 Tournament / Table Area

Start from summary:

```text
tournament.tables
```

Or request the stage tables directly:

```bash
curl "http://localhost:8080/tournaments/<tournamentId>/stages/<stageId>/tables"
```

### 4.6 Appeal / Moderation Demo

Use the appeal variant:

```bash
curl "http://localhost:8080/demo/summary?variant=Appeal&bootstrapIfMissing=true&refreshDerived=true"
```

Optional follow-up call:

```bash
curl "http://localhost:8080/appeals?status=Open&limit=20"
```

## 5. Frontend Integration Order

Recommended boot order:

1. Call `/demo/summary?variant=Basic&bootstrapIfMissing=true&refreshDerived=true`
2. Render cards and table area from the summary payload
3. Read `recommendedRequests` from the same payload
4. Use `/demo/readiness` if the UI wants a background readiness indicator
5. Switch to `variant=Leaderboard` or `variant=Appeal` for focused demo pages

## 6. Practical Notes

- `demo/summary` is the best default because it reduces frontend orchestration work.
- `demo/reset` is the best endpoint to call before a live presentation.
- `demo/guide` is useful when the frontend wants to show a scripted walkthrough or operator hints.
- `recommendedRequests` inside the summary payload already contains variant-aware next calls.
