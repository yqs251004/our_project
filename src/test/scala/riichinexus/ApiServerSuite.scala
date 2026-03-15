package riichinexus

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import munit.FunSuite

import riichinexus.api.*
import riichinexus.api.ApiModels.given
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

class ApiServerSuite extends FunSuite:
  private val client = HttpClient.newHttpClient()

  test("club applications endpoint returns submitted applications") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:00:00Z")

    val owner = app.playerService.registerPlayer("owner-1", "Owner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("Application Club", owner.id, now, owner.asPrincipal)

    app.clubService.applyForMembership(
      clubId = club.id,
      applicantUserId = Some("guest-1"),
      displayName = "Guest Applicant",
      message = Some("let me in")
    )

    withServer(app) { baseUrl =>
      val response = get(s"$baseUrl/clubs/${club.id.value}/applications")
      assertEquals(response.statusCode(), 200)

      val applications = readPage[ClubMembershipApplication](response.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.displayName, "Guest Applicant")
      assertEquals(applications.items.head.message, Some("let me in"))
    }
  }

  test("tournament whitelist and stage tables endpoints return seeded competition data") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T09:00:00Z")

    val admin = app.playerService.registerPlayer("admin-1", "Admin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("p2", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("p3", "Charlie", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      app.playerService.registerPlayer("p4", "Delta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    )

    val club = app.clubService.createClub("Whitelist Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player =>
      app.clubService.addMember(club.id, player.id, principalFor(app, admin.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Swiss Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Whitelist Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    app.tournamentService.whitelistClub(tournament.id, club.id, principalFor(app, admin.id))
    players.foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val whitelistResponse = get(s"$baseUrl/tournaments/${tournament.id.value}/whitelist")
      assertEquals(whitelistResponse.statusCode(), 200)
      val whitelist = readPage[TournamentWhitelistEntry](whitelistResponse.body())
      assert(whitelist.items.exists(_.clubId.contains(club.id)))
      assertEquals(
        whitelist.items.count(_.participantKind == TournamentParticipantKind.Player),
        players.size
      )

      val tablesResponse = get(s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/tables")
      assertEquals(tablesResponse.statusCode(), 200)
      val tables = readPage[Table](tablesResponse.body())
      assertEquals(tables.total, 1)
      assertEquals(tables.items.head.seats.map(_.playerId).toSet, players.map(_.id).toSet)
    }
  }

  test("record and appeal detail endpoints return persisted workflow objects") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T10:00:00Z")

    val admin = app.playerService.registerPlayer("admin-2", "Admin2", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("q2", "Echo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("q3", "Foxtrot", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      app.playerService.registerPlayer("q4", "Golf", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Operational Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Operations Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    ).head

    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))

    val appeal = app.appealService.fileAppeal(
      tableId = table.id,
      openedBy = players(1).id,
      description = "disconnect happened",
      actor = principalFor(app, players(1).id),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal was not created"))

    val adminPrincipal = principalFor(app, admin.id)
    app.appealService.adjudicateAppeal(
      ticketId = appeal.id,
      decision = AppealDecisionType.Resolve,
      verdict = "table reset approved",
      actor = adminPrincipal,
      tableResolution = Some(AppealTableResolution.ForceReset),
      adjudicatedAt = now.plusSeconds(120)
    )

    val resetTable = app.tableRepository.findById(table.id).getOrElse(fail("table missing after reset"))
    app.tableService.startTable(resetTable.id, now.plusSeconds(180), adminPrincipal)
    app.tableService.recordCompletedTable(
      resetTable.id,
      demoPaifuForResult(
        resetTable,
        tournament.id,
        stage.id,
        now.plusSeconds(240),
        winner = players(1).id,
        target = players(2).id
      ),
      adminPrincipal
    )

    val record = app.matchRecordRepository.findByTable(table.id).getOrElse(fail("match record missing"))

    withServer(app) { baseUrl =>
      val appealResponse = get(s"$baseUrl/appeals/${appeal.id.value}")
      assertEquals(appealResponse.statusCode(), 200)
      val storedAppeal = read[AppealTicket](appealResponse.body())
      assertEquals(storedAppeal.id, appeal.id)
      assertEquals(storedAppeal.status, AppealStatus.Resolved)

      val recordByIdResponse = get(s"$baseUrl/records/${record.id.value}")
      assertEquals(recordByIdResponse.statusCode(), 200)
      val recordById = read[MatchRecord](recordByIdResponse.body())
      assertEquals(recordById.id, record.id)

      val recordByTableResponse = get(s"$baseUrl/records/table/${table.id.value}")
      assertEquals(recordByTableResponse.statusCode(), 200)
      val recordByTable = read[MatchRecord](recordByTableResponse.body())
      assertEquals(recordByTable.id, record.id)
    }
  }

  test("dashboard endpoints enforce RBAC and allow scoped access") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T11:00:00Z")

    val owner = app.playerService.registerPlayer("dash-1", "Owner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val intruder = app.playerService.registerPlayer("dash-2", "Intruder", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val member = app.playerService.registerPlayer("dash-3", "Member", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1520)

    val club = app.clubService.createClub("Dashboard Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val forbiddenPlayer = get(
        s"$baseUrl/dashboards/players/${owner.id.value}?operatorId=${intruder.id.value}"
      )
      assertEquals(forbiddenPlayer.statusCode(), 403)

      val ownPlayer = get(
        s"$baseUrl/dashboards/players/${owner.id.value}?operatorId=${owner.id.value}"
      )
      assertEquals(ownPlayer.statusCode(), 200)

      val ownClub = get(
        s"$baseUrl/dashboards/clubs/${club.id.value}?operatorId=${owner.id.value}"
      )
      assertEquals(ownClub.statusCode(), 200)

      val forbiddenClub = get(
        s"$baseUrl/dashboards/clubs/${club.id.value}?operatorId=${member.id.value}"
      )
      assertEquals(forbiddenClub.statusCode(), 403)
    }
  }

  test("dictionary key and audit aggregate endpoints return targeted admin data") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:00:00Z")

    val root = app.playerService.registerPlayer("root-1", "Root", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val viewer = app.playerService.registerPlayer("root-2", "Viewer", RankSnapshot(RankPlatform.Custom, "A"), now, 1700)
    val admin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminPrincipal = principalFor(app, admin.id)

    val dictionaryEntry = app.superAdminService.upsertDictionary(
      key = "rank.formula",
      value = "uma+oka-v2",
      actor = adminPrincipal,
      note = Some("season 2"),
      updatedAt = now
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = get(s"$baseUrl/dictionary/${dictionaryEntry.key}")
      assertEquals(dictionaryResponse.statusCode(), 200)
      val storedEntry = read[GlobalDictionaryEntry](dictionaryResponse.body())
      assertEquals(storedEntry.value, dictionaryEntry.value)

      val forbiddenAudit = get(
        s"$baseUrl/audits/dictionary/${dictionaryEntry.key}?operatorId=${viewer.id.value}"
      )
      assertEquals(forbiddenAudit.statusCode(), 403)

      val auditResponse = get(
        s"$baseUrl/audits/dictionary/${dictionaryEntry.key}?operatorId=${admin.id.value}&eventType=GlobalDictionaryUpserted"
      )
      assertEquals(auditResponse.statusCode(), 200)
      val auditEntries = readPage[AuditEventEntry](auditResponse.body())
      assertEquals(auditEntries.total, 1)
      assertEquals(auditEntries.items.head.aggregateId, dictionaryEntry.key)
      assertEquals(auditEntries.items.head.eventType, "GlobalDictionaryUpserted")
    }
  }

  test("players and clubs list endpoints support shared pagination and filters") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:30:00Z")

    val owner = app.playerService.registerPlayer("page-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val alpha = app.playerService.registerPlayer("page-alpha", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val bravo = app.playerService.registerPlayer("page-bravo", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val suspended = app.playerService.registerPlayer("page-suspended", "Suspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1400)

    val club = app.clubService.createClub("Paged Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, alpha.id, principalFor(app, owner.id))
    app.clubService.addMember(club.id, bravo.id, principalFor(app, owner.id))
    app.clubService.addMember(club.id, suspended.id, principalFor(app, owner.id))
    app.playerRepository.save(suspended.copy(status = PlayerStatus.Suspended))

    val retiredClub = app.clubRepository.save(
      Club(
        id = IdGenerator.clubId(),
        name = "Retired Club",
        creator = owner.id,
        createdAt = now.minusSeconds(3600),
        members = Vector(owner.id),
        admins = Vector(owner.id),
        dissolvedAt = Some(now),
        dissolvedBy = Some(owner.id)
      )
    )

    withServer(app) { baseUrl =>
      val playersResponse = get(
        s"$baseUrl/players?clubId=${club.id.value}&status=Active&limit=1&offset=1"
      )
      assertEquals(playersResponse.statusCode(), 200)
      val playersPage = readPage[Player](playersResponse.body())
      assertEquals(playersPage.total, 3)
      assertEquals(playersPage.limit, 1)
      assertEquals(playersPage.offset, 1)
      assertEquals(playersPage.items.map(_.nickname), Vector("Bravo"))
      assertEquals(playersPage.appliedFilters("clubId"), club.id.value)
      assertEquals(playersPage.appliedFilters("status"), "Active")

      val clubsResponse = get(
        s"$baseUrl/clubs?memberId=${owner.id.value}&activeOnly=true&limit=10"
      )
      assertEquals(clubsResponse.statusCode(), 200)
      val clubsPage = readPage[Club](clubsResponse.body())
      assertEquals(clubsPage.total, 1)
      assertEquals(clubsPage.items.map(_.id), Vector(club.id))
      assert(!clubsPage.items.exists(_.id == retiredClub.id))
      assertEquals(clubsPage.appliedFilters("activeOnly"), "true")
    }
  }

  test("tables records and appeals endpoints support filtering and pagination") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:45:00Z")

    val admin = app.playerService.registerPlayer("filter-admin", "FilterAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else app.playerService.registerPlayer(
        s"filter-p$index",
        s"Player$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1500 + index
      )
    }
    val stage = TournamentStage(IdGenerator.stageId(), "Filter Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Filter Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val tables = app.tournamentService.scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    )

    val firstTable = tables.head
    val secondTable = tables.last
    app.tableService.startTable(firstTable.id, now.plusSeconds(60), principalFor(app, admin.id))
    val winner = firstTable.seats.head.playerId
    val target = firstTable.seats(1).playerId
    app.tableService.recordCompletedTable(
      firstTable.id,
      demoPaifuForResult(firstTable, tournament.id, stage.id, now.plusSeconds(120), winner, target),
      principalFor(app, admin.id)
    )

    app.appealService.fileAppeal(
      tableId = secondTable.id,
      openedBy = secondTable.seats.head.playerId,
      description = "waiting for adjudication",
      actor = principalFor(app, secondTable.seats.head.playerId),
      createdAt = now.plusSeconds(180)
    )

    withServer(app) { baseUrl =>
      val tablesResponse = get(
        s"$baseUrl/tables?tournamentId=${tournament.id.value}&status=AppealInProgress&limit=5"
      )
      assertEquals(tablesResponse.statusCode(), 200)
      val tablesPage = readPage[Table](tablesResponse.body())
      assertEquals(tablesPage.total, 1)
      assertEquals(tablesPage.items.map(_.id), Vector(secondTable.id))

      val recordsResponse = get(
        s"$baseUrl/records?tournamentId=${tournament.id.value}&playerId=${winner.value}&limit=1"
      )
      assertEquals(recordsResponse.statusCode(), 200)
      val recordsPage = readPage[MatchRecord](recordsResponse.body())
      assertEquals(recordsPage.total, 1)
      assertEquals(recordsPage.items.head.tableId, firstTable.id)

      val appealsResponse = get(
        s"$baseUrl/appeals?tournamentId=${tournament.id.value}&status=Open&limit=1"
      )
      assertEquals(appealsResponse.statusCode(), 200)
      val appealsPage = readPage[AppealTicket](appealsResponse.body())
      assertEquals(appealsPage.total, 1)
      assertEquals(appealsPage.items.head.tableId, secondTable.id)
      assertEquals(appealsPage.appliedFilters("status"), "Open")
    }
  }

  test("dictionary and audit collection endpoints support filters and pagination") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:55:00Z")

    val root = app.playerService.registerPlayer("page-root", "Root", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val admin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminPrincipal = principalFor(app, admin.id)

    val rankFormula = app.superAdminService.upsertDictionary(
      key = "rank.formula",
      value = "uma+oka-v3",
      actor = adminPrincipal,
      updatedAt = now
    )
    app.superAdminService.upsertDictionary(
      key = "rank.scale",
      value = "aggressive",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(60)
    )
    app.superAdminService.upsertDictionary(
      key = "stage.pool",
      value = "4",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(120)
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = get(s"$baseUrl/dictionary?prefix=rank.&limit=1")
      assertEquals(dictionaryResponse.statusCode(), 200)
      val dictionaryPage = readPage[GlobalDictionaryEntry](dictionaryResponse.body())
      assertEquals(dictionaryPage.total, 2)
      assertEquals(dictionaryPage.items.size, 1)
      assertEquals(dictionaryPage.items.head.key, "rank.formula")

      val auditResponse = get(
        s"$baseUrl/audits?operatorId=${admin.id.value}&aggregateType=dictionary&actorId=${admin.id.value}&limit=1"
      )
      assertEquals(auditResponse.statusCode(), 200)
      val auditPage = readPage[AuditEventEntry](auditResponse.body())
      assertEquals(auditPage.total, 3)
      assertEquals(auditPage.items.head.aggregateId, rankFormula.key)
      assertEquals(auditPage.items.head.eventType, "GlobalDictionaryUpserted")
      assertEquals(auditPage.appliedFilters("aggregateType"), "dictionary")
    }
  }

  test("club management endpoints revoke admin and remove member") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T13:00:00Z")

    val owner = app.playerService.registerPlayer("club-owner", "ClubOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val vice = app.playerService.registerPlayer("club-vice", "ClubVice", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val member = app.playerService.registerPlayer("club-member", "ClubMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)

    val club = app.clubService.createClub("Managed Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, vice.id, principalFor(app, owner.id))
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))
    app.clubService.assignAdmin(club.id, vice.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val revokeResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/admins/${vice.id.value}/revoke",
        write(OperatorRequest(Some(owner.id.value)))
      )
      assertEquals(revokeResponse.statusCode(), 200)
      val updatedClub = read[Club](revokeResponse.body())
      assertEquals(updatedClub.admins, Vector(owner.id))

      val removeResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/members/${member.id.value}/remove",
        write(OperatorRequest(Some(owner.id.value)))
      )
      assertEquals(removeResponse.statusCode(), 200)
      val removedClub = read[Club](removeResponse.body())
      assertEquals(removedClub.members.toSet, Set(owner.id, vice.id))
      assertEquals(app.playerRepository.findById(member.id).map(_.boundClubIds), Some(Vector.empty))
    }
  }

  test("tournament admin revoke endpoint removes scoped admin role") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T14:00:00Z")

    val root = app.playerService.registerPlayer("tour-root", "TournamentRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val adminA = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminB = app.playerService.registerPlayer("tour-admin-b", "TournamentB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val stage = TournamentStage(IdGenerator.stageId(), "Admin Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Admin Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage),
      adminId = Some(adminA.id)
    )
    app.tournamentService.assignTournamentAdmin(
      tournament.id,
      adminB.id,
      principalFor(app, adminA.id)
    )

    withServer(app) { baseUrl =>
      val revokeResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/admins/${adminB.id.value}/revoke",
        write(OperatorRequest(Some(adminA.id.value)))
      )
      assertEquals(revokeResponse.statusCode(), 200)

      val updatedTournament = read[Tournament](revokeResponse.body())
      assertEquals(updatedTournament.admins, Vector(adminA.id))
      val updatedAdmin = app.playerRepository.findById(adminB.id).getOrElse(fail("missing adminB"))
      assert(!updatedAdmin.roleGrants.exists(grant =>
        grant.role == RoleKind.TournamentAdmin && grant.tournamentId.contains(tournament.id)
      ))
    }
  }

  private def withServer[A](app: ApplicationContext)(f: String => A): A =
    val server = RiichiNexusApiServer(
      app,
      ApiServerConfig(host = "127.0.0.1", port = 0, storageLabel = "memory")
    )

    server.start()
    try f(s"http://127.0.0.1:${server.port}")
    finally server.stop()

  private def get(url: String): HttpResponse[String] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private def postJson(url: String, body: String): HttpResponse[String] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private def principalFor(app: ApplicationContext, playerId: PlayerId): AccessPrincipal =
    app.playerRepository.findById(playerId).getOrElse(fail(s"player ${playerId.value} missing")).asPrincipal

  private def readPage[T: Reader](body: String): PagedResponse[T] =
    read[PagedResponse[T]](body)

  private def demoPaifuForResult(
      table: Table,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      recordedAt: Instant,
      winner: PlayerId,
      target: PlayerId
  ): Paifu =
    val orderedSeats = table.seats.sortBy(_.seat.ordinal)
    val east = orderedSeats(0).playerId
    val south = orderedSeats(1).playerId
    val west = orderedSeats(2).playerId
    val north = orderedSeats(3).playerId
    val seatByPlayer = table.seats.map(seat => seat.playerId -> seat.seat).toMap
    val untouchedPlayers = orderedSeats.map(_.playerId).filterNot(playerId =>
      playerId == winner || playerId == target
    )
    val secondPlayer = untouchedPlayers.headOption.getOrElse(east)
    val thirdPlayer = untouchedPlayers.drop(1).headOption.getOrElse(north)
    val finalPoints = Map(
      winner -> 32700,
      secondPlayer -> 25000,
      thirdPlayer -> 25000,
      target -> 17300
    )
    val placements = Vector(winner, secondPlayer, thirdPlayer, target)

    Paifu(
      id = IdGenerator.paifuId(),
      metadata = PaifuMetadata(
        recordedAt = recordedAt,
        source = "api-test-fixture",
        tableId = table.id,
        tournamentId = tournamentId,
        stageId = stageId,
        seats = table.seats
      ),
      rounds = Vector(
        KyokuRecord(
          descriptor = KyokuDescriptor(SeatWind.East, 1, 0),
          initialHands = table.seats.map(seat => seat.playerId -> Vector("1m", "1p", "1s")).toMap,
          actions = Vector(
            PaifuAction(1, Some(east), PaifuActionType.Draw, Some("4m"), Some(3)),
            PaifuAction(2, Some(winner), PaifuActionType.Riichi, note = Some("riichi")),
            PaifuAction(3, Some(winner), PaifuActionType.Win, Some("7p"), Some(0))
          ),
          result = AgariResult(
            outcome = HandOutcome.Ron,
            winner = Some(winner),
            target = Some(target),
            han = Some(3),
            fu = Some(40),
            yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Ippatsu", 1)),
            points = 7700,
            scoreChanges = Vector(
              ScoreChange(east, if east == winner then 7700 else if east == target then -7700 else 0),
              ScoreChange(south, if south == winner then 7700 else if south == target then -7700 else 0),
              ScoreChange(west, if west == winner then 7700 else if west == target then -7700 else 0),
              ScoreChange(north, if north == winner then 7700 else if north == target then -7700 else 0)
            )
          )
        )
      ),
      finalStandings = placements.zipWithIndex.map { case (playerId, index) =>
        FinalStanding(
          playerId,
          seatByPlayer(playerId),
          finalPoints(playerId),
          index + 1
        )
      }
    )

