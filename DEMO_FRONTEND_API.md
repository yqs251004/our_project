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
- available interactive demo actions
- readiness state for derived views

For chart-friendly UI sections, there is also a dedicated widgets payload:

```bash
curl "http://localhost:8080/demo/widgets?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

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

### 3.4 Widgets

Use this when the frontend wants chart-ready aggregates without reprocessing the full summary payload:

```bash
curl "http://localhost:8080/demo/widgets?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

This response is intended for:
- headline KPI cards
- ELO bar charts
- club power charts
- leaderboard preview strips
- table status donuts / stacked bars
- readiness indicators

### 3.5 Bootstrap

Create demo data explicitly:

```bash
curl -X POST "http://localhost:8080/demo/bootstrap?variant=Basic&refreshDerived=true"
```

### 3.6 Refresh

Refresh an existing scenario without changing its overall variant:

```bash
curl -X POST "http://localhost:8080/demo/refresh?variant=Basic&bootstrapIfMissing=true"
```

### 3.7 Reset

Reset a variant back to its seeded baseline:

```bash
curl -X POST "http://localhost:8080/demo/reset?variant=Basic&refreshDerived=true"
```

### 3.8 Action Catalog

Use this when the frontend wants to render presenter/demo buttons dynamically:

```bash
curl "http://localhost:8080/demo/actions?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

The same action list is also embedded inside:

```text
demo/summary.availableActions
```

### 3.9 Execute Demo Actions

Archive the next runnable table:

```bash
curl -X POST "http://localhost:8080/demo/actions/ArchiveNextTable?variant=Basic&bootstrapIfMissing=true"
```

Create an appeal on the next eligible table:

```bash
curl -X POST "http://localhost:8080/demo/actions/FileOpenAppeal?variant=Appeal&bootstrapIfMissing=true"
```

Resolve the oldest active appeal:

```bash
curl -X POST "http://localhost:8080/demo/actions/ResolveOldestAppeal?variant=Appeal&bootstrapIfMissing=true"
```

Refresh or reset from the same action endpoint:

```bash
curl -X POST "http://localhost:8080/demo/actions/RefreshScenario?variant=Basic&bootstrapIfMissing=true"
```

```bash
curl -X POST "http://localhost:8080/demo/actions/ResetScenario?variant=Basic&bootstrapIfMissing=true"
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

Optional chart payload:

```bash
curl "http://localhost:8080/demo/widgets?variant=Basic&bootstrapIfMissing=true&refreshDerived=true"
```

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

Recommended chart payload:

```bash
curl "http://localhost:8080/demo/widgets?variant=Leaderboard&bootstrapIfMissing=true&refreshDerived=true"
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

Interactive moderation actions:

```bash
curl "http://localhost:8080/demo/actions?variant=Appeal&bootstrapIfMissing=true&refreshDerived=true"
```

## 5. Frontend Integration Order

Recommended boot order:

1. Call `/demo/summary?variant=Basic&bootstrapIfMissing=true&refreshDerived=true`
2. Call `/demo/widgets?variant=Basic&bootstrapIfMissing=true&refreshDerived=true` for chart sections
3. Read `availableActions` from the same summary payload and render a demo action bar
4. Render cards and table area from the summary payload
5. Read `recommendedRequests` from the same payload
6. Use `/demo/readiness` if the UI wants a background readiness indicator
7. Switch to `variant=Leaderboard` or `variant=Appeal` for focused demo pages

## 6. Practical Notes

- `demo/summary` is the best default because it reduces frontend orchestration work.
- `demo/widgets` is the best companion endpoint for homepage charts and compact KPI components.
- `demo/actions` is the best endpoint for live demo buttons when the frontend wants to mutate seeded state.
- `demo/reset` is the best endpoint to call before a live presentation.
- `demo/guide` is useful when the frontend wants to show a scripted walkthrough or operator hints.
- `recommendedRequests` and `availableActions` inside the summary payload already contain variant-aware next calls.
