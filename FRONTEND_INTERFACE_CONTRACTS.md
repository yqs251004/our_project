# RiichiNexus Frontend Interface Contracts

This document captures the stable backend response shapes the frontend is expected to consume next.

Base URL examples assume:

```text
http://localhost:8080
```

## 0. Generated Contract Docs

### GET `/openapi.json`

Returns the generated OpenAPI 3.1 contract for the frontend-facing backend interfaces covered in this document.

### GET `/swagger`

Returns a Swagger UI page that loads `/openapi.json` for interactive inspection during integration and review.

## 1. Session

### GET `/session`

Query:

- `operatorId` for a registered player session
- `guestSessionId` for a guest session

Example:

```bash
curl "http://localhost:8080/session?operatorId=player-123"
```

Stable shape:

```json
{
  "principalKind": "RegisteredPlayer",
  "principalId": "player-123",
  "displayName": "Alice",
  "authenticated": true,
  "roles": {
    "isGuest": false,
    "isRegisteredPlayer": true,
    "isClubAdmin": true,
    "isTournamentAdmin": false,
    "isSuperAdmin": false
  },
  "player": {},
  "guestSession": null
}
```

### GET `/players/me`

Query:

- `operatorId`

Returns the canonical `Player` aggregate for the current registered player.

## 2. Club Application Inbox

### GET `/clubs/:clubId/applications`

Query:

- `operatorId`
- `status`
- `applicantUserId`
- `displayName`
- standard paging params `limit`, `offset`

Example:

```bash
curl "http://localhost:8080/clubs/club-123/applications?operatorId=player-admin&status=Pending&limit=20"
```

Paged item shape:

```json
{
  "applicationId": "membership-123",
  "clubId": "club-123",
  "clubName": "EastWind Club",
  "applicant": {
    "playerId": "player-234",
    "applicantUserId": "demo-bob",
    "displayName": "Bob",
    "playerStatus": "Active",
    "currentRank": {},
    "elo": 1540,
    "clubIds": []
  },
  "submittedAt": "2026-03-29T10:00:00Z",
  "message": "I'd like to join.",
  "status": "Pending",
  "reviewedBy": null,
  "reviewedByDisplayName": null,
  "reviewedAt": null,
  "reviewNote": null,
  "withdrawnByPrincipalId": null,
  "canReview": true,
  "canWithdraw": false
}
```

### GET `/clubs/:clubId/applications/:membershipId`

Query:

- `operatorId` or `guestSessionId`

Returns the same stable `ClubMembershipApplicationView` shape as the inbox list.

### POST `/clubs/:clubId/applications/:membershipId/review`

Body:

```json
{
  "operatorId": "player-admin",
  "decision": "approve",
  "note": "Accepted for the spring roster."
}
```

Supported decisions:

- `approve`
- `reject`

Optional field for guest-origin applications:

```json
{
  "operatorId": "player-admin",
  "decision": "approve",
  "playerId": "player-234",
  "note": "Bound guest application to the registered player record."
}
```

Returns the reviewed `ClubMembershipApplicationView`.

Note:

- when a guest session is upgraded to a registered player, pending guest-origin applications are automatically rebound to that player's `userId`
- because of that, the frontend should usually not need `playerId` during a normal approve flow after account upgrade

## 3. Club Discovery

### GET `/clubs`

Important query filters:

- `activeOnly=true`
- `joinableOnly=true`

`joinableOnly=true` is now driven by the club's configured recruitment policy:

- club is not dissolved
- `recruitmentPolicy.applicationsOpen == true`

### POST `/clubs/:clubId/recruitment-policy`

Admin-only configuration endpoint for club recruitment metadata.

Body:

```json
{
  "operatorId": "player-admin",
  "applicationsOpen": true,
  "requirementsText": "Please have at least one recent ranked match record.",
  "expectedReviewSlaHours": 48,
  "note": "Adjusted for open recruiting week."
}
```

Returns the updated `Club` aggregate, including:

```json
{
  "recruitmentPolicy": {
    "applicationsOpen": true,
    "requirementsText": "Please have at least one recent ranked match record.",
    "expectedReviewSlaHours": 48
  }
}
```

## 4. Public Club Detail

### GET `/public/clubs/:clubId`

Stable shape:

```json
{
  "clubId": "club-123",
  "name": "EastWind Club",
  "memberCount": 4,
  "activeMemberCount": 4,
  "adminCount": 1,
  "powerRating": 1562.5,
  "totalPoints": 320,
  "treasuryBalance": 120000,
  "pointPool": 320,
  "relations": [],
  "honors": [],
  "applicationPolicy": {
    "applicationsOpen": true,
    "requirementsText": "Please have at least one recent ranked match record.",
    "expectedReviewSlaHours": 48,
    "pendingApplicationCount": 2
  },
  "currentLineup": [],
  "recentMatches": []
}
```

Notes:

- `currentLineup` is the current active member snapshot used for public club panels.
- when the club has submitted an official stage lineup, `currentLineup` prefers the latest submitted active lineup over the full member list
- `recentMatches` is the latest club-related archived match list.

## 5. Tournament Stage Directory

### GET `/tournaments/:id/stages`

Stable item shape:

```json
{
  "stageId": "stage-demo-swiss",
  "name": "Swiss Stage 1",
  "format": "Swiss",
  "order": 1,
  "status": "Active",
  "currentRound": 1,
  "roundCount": 3,
  "schedulingPoolSize": 4,
  "pendingTablePlanCount": 0,
  "scheduledTableCount": 2
}
```

## 6. Public Tournament Index

### GET `/public/tournaments`

Stable list item shape:

```json
{
  "tournamentId": "tournament-123",
  "name": "RiichiNexus Spring Demo",
  "organizer": "Frontend Demo",
  "status": "InProgress",
  "startsAt": "2026-03-29T09:00:00Z",
  "endsAt": "2026-03-29T18:00:00Z",
  "stageCount": 2,
  "activeStageCount": 1,
  "participantCount": 8,
  "clubCount": 2,
  "playerCount": 2
}
```

## 7. Public Tournament Detail

### GET `/public/tournaments/:id`

Stable shape:

```json
{
  "tournamentId": "tournament-123",
  "name": "RiichiNexus Spring Demo",
  "organizer": "Frontend Demo",
  "status": "InProgress",
  "startsAt": "2026-03-29T09:00:00Z",
  "endsAt": "2026-03-29T18:00:00Z",
  "clubIds": ["club-1", "club-2"],
  "playerIds": ["player-1", "player-2"],
  "whitelistCount": 4,
  "stages": [
    {
      "stageId": "stage-demo-swiss",
      "name": "Swiss Stage 1",
      "format": "Swiss",
      "order": 1,
      "status": "Active",
      "currentRound": 1,
      "roundCount": 3,
      "tableCount": 2,
      "archivedTableCount": 1,
      "pendingTablePlanCount": 0,
      "standings": {},
      "bracket": null
    }
  ]
}
```

Notes:

- `standings` uses the same shape as `GET /tournaments/:id/stages/:stageId/standings`
- `bracket` uses the same shape as `GET /tournaments/:id/stages/:stageId/bracket`
- knockout/finals stages will return a non-null `bracket`

## 8. Existing High-Value Endpoints Still Supported

- `GET /tournaments/:id/stages/:stageId/standings`
- `GET /tournaments/:id/stages/:stageId/bracket`
- `POST /tables/:tableId/seats/:seat/state`
- `GET /appeals`
- `GET /advanced-stats/players/:playerId`
- `GET /advanced-stats/clubs/:clubId`

These endpoints remain part of the current frontend integration path.
