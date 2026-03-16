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

  test("guest session endpoints support anonymous club applications") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")

    val owner = app.playerService.registerPlayer("guest-api-owner", "GuestApiOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("Guest API Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/guest-sessions",
        write(CreateGuestSessionRequest(Some("AnonymousFan")))
      )
      assertEquals(createResponse.statusCode(), 201)
      val session = read[GuestAccessSession](createResponse.body())
      assertEquals(session.displayName, "AnonymousFan")

      val detailResponse = get(s"$baseUrl/guest-sessions/${session.id.value}")
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[GuestAccessSession](detailResponse.body()).id, session.id)

      val applicationResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications",
        write(
          ClubMembershipApplicationRequest(
            applicantUserId = None,
            displayName = "ignored-by-session",
            message = Some("guest route test"),
            guestSessionId = Some(session.id.value)
          )
        )
      )
      assertEquals(applicationResponse.statusCode(), 200)
      val application = read[ClubMembershipApplication](applicationResponse.body())
      assertEquals(application.displayName, "AnonymousFan")
      assertEquals(application.applicantUserId, Some(s"guest:${session.id.value}"))

      val listResponse = get(s"$baseUrl/clubs/${club.id.value}/applications")
      assertEquals(listResponse.statusCode(), 200)
      val applications = readPage[ClubMembershipApplication](listResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.id, application.id)
    }
  }

  test("registered players can submit and withdraw club applications over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:45:00Z")

    val owner = app.playerService.registerPlayer("api-withdraw-owner", "ApiWithdrawOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val applicant = app.playerService.registerPlayer("api-withdraw-player", "ApiWithdrawPlayer", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val club = app.clubService.createClub("Withdraw API Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications",
        write(
          ClubMembershipApplicationRequest(
            applicantUserId = None,
            displayName = "fallback-name",
            message = Some("registered path"),
            guestSessionId = None,
            operatorId = Some(applicant.id.value)
          )
        )
      )
      assertEquals(createResponse.statusCode(), 200)
      val created = read[ClubMembershipApplication](createResponse.body())
      assertEquals(created.applicantUserId, Some(applicant.userId))
      assertEquals(created.displayName, applicant.nickname)

      val withdrawResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications/${created.id.value}/withdraw",
        write(WithdrawClubApplicationRequest(operatorId = Some(applicant.id.value), note = Some("not this season")))
      )
      assertEquals(withdrawResponse.statusCode(), 200)
      val withdrawn = read[ClubMembershipApplication](withdrawResponse.body())
      assertEquals(withdrawn.status, ClubMembershipApplicationStatus.Withdrawn)
      assertEquals(withdrawn.withdrawnByPrincipalId, Some(applicant.id.value))

      val filteredResponse = get(
        s"$baseUrl/clubs/${club.id.value}/applications?status=Withdrawn&applicantUserId=${applicant.userId}"
      )
      assertEquals(filteredResponse.statusCode(), 200)
      val applications = readPage[ClubMembershipApplication](filteredResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.id, created.id)
    }
  }

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

  test("dictionary API changes default tournament settlement ratios at runtime") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T13:05:00Z")

    val root = app.playerService.registerPlayer("dict-api-root", "DictApiRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val admin = app.playerService.registerPlayer("dict-api-admin", "DictApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("dict-api-b", "DictApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("dict-api-c", "DictApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("dict-api-d", "DictApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "API Settlement Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "API Dictionary Settlement Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    app.tableService.recordCompletedTable(
      table.id,
      demoPaifuForResult(
        table,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = table.seats.head.playerId,
        target = table.seats(1).playerId
      ),
      principalFor(app, admin.id)
    )
    app.tournamentService.completeStage(
      tournament.id,
      stage.id,
      principalFor(app, admin.id),
      now.plusSeconds(180)
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = postJson(
        s"$baseUrl/admin/dictionary",
        write(UpsertDictionaryRequest(superAdmin.id.value, "settlement.defaultPayoutRatios", "0.6,0.25,0.15", Some("runtime payout tuning")))
      )
      assertEquals(dictionaryResponse.statusCode(), 201)

      val settleResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settle",
        write(SettleTournamentRequest(admin.id.value, stage.id.value, 1000))
      )
      assertEquals(settleResponse.statusCode(), 200)
      val settlement = read[TournamentSettlementSnapshot](settleResponse.body())
      assertEquals(settlement.entries.take(3).map(_.awardAmount), Vector(600L, 250L, 150L))
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

  test("club operation endpoints update treasury point pool and rank tree") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:00:00Z")

    val owner = app.playerService.registerPlayer("club-fin-owner", "ClubFinOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = app.clubService.createClub("Club Finance", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val treasuryResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/treasury",
        write(AdjustClubTreasuryRequest(owner.id.value, 2500L, Some("sponsor")))
      )
      assertEquals(treasuryResponse.statusCode(), 200)
      assertEquals(read[Club](treasuryResponse.body()).treasuryBalance, 2500L)

      val pointPoolResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/point-pool",
        write(AdjustClubPointPoolRequest(owner.id.value, 180, Some("event reward")))
      )
      assertEquals(pointPoolResponse.statusCode(), 200)
      assertEquals(read[Club](pointPoolResponse.body()).pointPool, 180)

      val rankTreeResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/rank-tree",
        write(
          UpdateClubRankTreeRequest(
            operatorId = owner.id.value,
            ranks = Vector(
              ClubRankNodeRequest("rookie", "Rookie", 0),
              ClubRankNodeRequest("elite", "Elite", 1500, Vector("priority-lineup"))
            ),
            note = Some("season refresh")
          )
        )
      )
      assertEquals(rankTreeResponse.statusCode(), 200)
      assertEquals(read[Club](rankTreeResponse.body()).rankTree.map(_.code), Vector("rookie", "elite"))
    }
  }

  test("club member privilege endpoints expose delegated rank capabilities") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:03:00Z")

    val owner = app.playerService.registerPlayer("club-priv-owner", "ClubPrivOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val delegate = app.playerService.registerPlayer("club-priv-delegate", "ClubPrivDelegate", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val club = app.clubService.createClub("Club Privileges", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, delegate.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val rankTreeResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/rank-tree",
        write(
          UpdateClubRankTreeRequest(
            operatorId = owner.id.value,
            ranks = Vector(
              ClubRankNodeRequest("rookie", "Rookie", 0),
              ClubRankNodeRequest("officer", "Officer", 100, Vector("manage-bank", "priority-lineup"))
            )
          )
        )
      )
      assertEquals(rankTreeResponse.statusCode(), 200)

      val contributionResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/member-contributions",
        write(AdjustClubMemberContributionRequest(owner.id.value, delegate.id.value, 120, Some("season contribution")))
      )
      assertEquals(contributionResponse.statusCode(), 200)

      val detailResponse = get(s"$baseUrl/clubs/${club.id.value}/member-privileges/${delegate.id.value}")
      assertEquals(detailResponse.statusCode(), 200)
      val detail = read[ClubMemberPrivilegeSnapshot](detailResponse.body())
      assertEquals(detail.rankCode, "officer")
      assertEquals(detail.privileges, Vector("manage-bank", "priority-lineup"))

      val listResponse = get(s"$baseUrl/clubs/${club.id.value}/member-privileges?privilege=manage-bank")
      assertEquals(listResponse.statusCode(), 200)
      val page = readPage[ClubMemberPrivilegeSnapshot](listResponse.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.head.playerId, delegate.id)

      val delegatedTreasury = postJson(
        s"$baseUrl/clubs/${club.id.value}/treasury",
        write(AdjustClubTreasuryRequest(delegate.id.value, 900L, Some("delegated bank access")))
      )
      assertEquals(delegatedTreasury.statusCode(), 200)
      assertEquals(read[Club](delegatedTreasury.body()).treasuryBalance, 900L)
    }
  }

  test("club title API supports clearing assigned titles") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:05:00Z")

    val owner = app.playerService.registerPlayer("api-title-owner", "ApiTitleOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = app.playerService.registerPlayer("api-title-member", "ApiTitleMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("API Title Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val assignResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/titles",
        write(AssignClubTitleRequest(member.id.value, owner.id.value, "Vice Captain", Some("promotion")))
      )
      assertEquals(assignResponse.statusCode(), 200)
      assertEquals(read[Club](assignResponse.body()).titleAssignments.map(_.title), Vector("Vice Captain"))

      val clearResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/titles/${member.id.value}/clear",
        write(ClearClubTitleRequest(owner.id.value, Some("rotation")))
      )
      assertEquals(clearResponse.statusCode(), 200)
      assertEquals(read[Club](clearResponse.body()).titleAssignments, Vector.empty)
    }
  }

  test("club relation endpoint keeps reciprocal mappings in sync") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:15:00Z")

    val ownerA = app.playerService.registerPlayer("api-relation-a", "ApiRelationA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val ownerB = app.playerService.registerPlayer("api-relation-b", "ApiRelationB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1780)
    val clubA = app.clubService.createClub("API Alliance A", ownerA.id, now, ownerA.asPrincipal)
    val clubB = app.clubService.createClub("API Alliance B", ownerB.id, now, ownerB.asPrincipal)

    withServer(app) { baseUrl =>
      val allianceResponse = postJson(
        s"$baseUrl/clubs/${clubA.id.value}/relations",
        write(UpdateClubRelationRequest(ownerA.id.value, clubB.id.value, "Alliance", Some("partner")))
      )
      assertEquals(allianceResponse.statusCode(), 200)
      assertEquals(read[Club](allianceResponse.body()).relations.map(_.targetClubId), Vector(clubB.id))
      assertEquals(
        app.clubRepository.findById(clubB.id).map(_.relations.map(_.targetClubId)),
        Some(Vector(clubA.id))
      )

      val neutralResponse = postJson(
        s"$baseUrl/clubs/${clubA.id.value}/relations",
        write(UpdateClubRelationRequest(ownerA.id.value, clubB.id.value, "Neutral", Some("reset")))
      )
      assertEquals(neutralResponse.statusCode(), 200)
      assertEquals(read[Club](neutralResponse.body()).relations, Vector.empty)
      assertEquals(app.clubRepository.findById(clubB.id).map(_.relations), Some(Vector.empty))
    }
  }

  test("stage table endpoints expose round filters for multi-round scheduling") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:30:00Z")

    val admin = app.playerService.registerPlayer("round-admin", "RoundAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else app.playerService.registerPlayer(
        s"round-p$index",
        s"RoundPlayer$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1600 - index
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Round Stage", StageFormat.Swiss, 1, 2, schedulingPoolSize = 1)
    val tournament = app.tournamentService.createTournament(
      "Round Filter Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))

    val firstRoundTable = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    app.tableService.startTable(firstRoundTable.id, now.plusSeconds(60), principalFor(app, admin.id))
    app.tableService.recordCompletedTable(
      firstRoundTable.id,
      demoPaifuForResult(firstRoundTable, tournament.id, stage.id, now.plusSeconds(120), firstRoundTable.seats.head.playerId, firstRoundTable.seats(1).playerId),
      principalFor(app, admin.id)
    )
    val secondRoundOne = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).last
    app.tableService.startTable(secondRoundOne.id, now.plusSeconds(180), principalFor(app, admin.id))
    app.tableService.recordCompletedTable(
      secondRoundOne.id,
      demoPaifuForResult(secondRoundOne, tournament.id, stage.id, now.plusSeconds(240), secondRoundOne.seats.head.playerId, secondRoundOne.seats(1).playerId),
      principalFor(app, admin.id)
    )
    app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val stageTablesResponse = get(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/tables?roundNumber=2"
      )
      assertEquals(stageTablesResponse.statusCode(), 200)
      val stageTables = readPage[Table](stageTablesResponse.body())
      assertEquals(stageTables.total, 1)
      assertEquals(stageTables.items.head.stageRoundNumber, 2)

      val globalTablesResponse = get(
        s"$baseUrl/tables?tournamentId=${tournament.id.value}&roundNumber=2"
      )
      assertEquals(globalTablesResponse.statusCode(), 200)
      val globalTables = readPage[Table](globalTablesResponse.body())
      assertEquals(globalTables.total, 1)
      assertEquals(globalTables.items.head.stageRoundNumber, 2)
    }
  }


  test("stage rules API can switch swiss pairingMethod to snake") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:45:00Z")

    val admin = app.playerService.registerPlayer("pair-api-admin", "PairApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else app.playerService.registerPlayer(
        s"pair-api-$index",
        s"PairApi$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1800 - index * 40
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Pair API Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Pair API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val rulesResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/rules",
        write(
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = "SwissCut",
            cutSize = Some(8),
            pairingMethod = Some("snake")
          )
        )
      )
      assertEquals(rulesResponse.statusCode(), 200)
      assertEquals(read[Tournament](rulesResponse.body()).stages.head.swissRule.map(_.pairingMethod), Some("snake"))

      val scheduleResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/schedule",
        write(OperatorRequest(Some(admin.id.value)))
      )
      assertEquals(scheduleResponse.statusCode(), 200)
      val tables = read[Vector[Table]](scheduleResponse.body()).sortBy(_.tableNo)
      val nicknameById = players.map(player => player.id -> player.nickname).toMap
      val groupedNicknames = tables.map(table => table.seats.map(seat => nicknameById(seat.playerId)).toSet)
      assertEquals(
        groupedNicknames,
        Vector(
          Set(players(0).nickname, players(3).nickname, players(4).nickname, players(7).nickname),
          Set(players(1).nickname, players(2).nickname, players(5).nickname, players(6).nickname)
        )
      )
    }
  }

  test("custom stage rules can limit scheduled tables through targetTableCount") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:55:00Z")

    val admin = app.playerService.registerPlayer("custom-api-admin", "CustomApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else app.playerService.registerPlayer(
        s"custom-api-$index",
        s"CustomApi$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1800 - index * 30
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Custom API Stage", StageFormat.Custom, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Custom API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val rulesResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/rules",
        write(
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = "Custom",
            targetTableCount = Some(1),
            note = Some("top=4")
          )
        )
      )
      assertEquals(rulesResponse.statusCode(), 200)

      val scheduleResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/schedule",
        write(OperatorRequest(Some(admin.id.value)))
      )
      assertEquals(scheduleResponse.statusCode(), 200)
      val tables = read[Vector[Table]](scheduleResponse.body())
      assertEquals(tables.size, 1)
      assertEquals(tables.head.seats.map(_.playerId).toSet, players.take(4).map(_.id).toSet)
    }
  }

  test("stage standings endpoint honors swiss carryOverPoints flag") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T16:00:00Z")

    val admin = app.playerService.registerPlayer("carry-api-admin", "CarryApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("carry-api-b", "CarryApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      app.playerService.registerPlayer("carry-api-c", "CarryApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      app.playerService.registerPlayer("carry-api-d", "CarryApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    val stage = TournamentStage(
      IdGenerator.stageId(),
      "Carry API Stage",
      StageFormat.Swiss,
      1,
      2,
      swissRule = Some(SwissRuleConfig(carryOverPoints = false))
    )
    val tournament = app.tournamentService.createTournament(
      "Carry API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))

    val roundOne = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    roundOne.seats.foreach(seat =>
      app.tableService.updateSeatState(roundOne.id, seat.seat, principalFor(app, seat.playerId), ready = Some(true))
    )
    app.tableService.startTable(roundOne.id, now.plusSeconds(60), principalFor(app, admin.id))
    app.tableService.recordCompletedTable(
      roundOne.id,
      demoPaifuForResult(roundOne, tournament.id, stage.id, now.plusSeconds(120), roundOne.seats(0).playerId, roundOne.seats(3).playerId),
      principalFor(app, admin.id)
    )

    val roundTwo = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))
      .find(_.stageRoundNumber == 2)
      .getOrElse(fail("round two table missing"))
    roundTwo.seats.foreach(seat =>
      app.tableService.updateSeatState(roundTwo.id, seat.seat, principalFor(app, seat.playerId), ready = Some(true))
    )
    app.tableService.startTable(roundTwo.id, now.plusSeconds(180), principalFor(app, admin.id))
    app.tableService.recordCompletedTable(
      roundTwo.id,
      demoPaifuForResult(roundTwo, tournament.id, stage.id, now.plusSeconds(240), roundTwo.seats(1).playerId, roundTwo.seats(0).playerId),
      principalFor(app, admin.id)
    )

    withServer(app) { baseUrl =>
      val response = get(s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/standings")
      assertEquals(response.statusCode(), 200)
      val standings = read[StageRankingSnapshot](response.body())
      assertEquals(standings.entries.map(_.playerId).take(4), Vector(
        roundTwo.seats(1).playerId,
        roundTwo.seats(2).playerId,
        roundTwo.seats(3).playerId,
        roundTwo.seats(0).playerId
      ))
      assertEquals(standings.entries.find(_.playerId == roundTwo.seats(0).playerId).map(_.matchesPlayed), Some(1))
    }
  }

  test("table seat state endpoint controls readiness and disconnect flow") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T16:30:00Z")

    val admin = app.playerService.registerPlayer("seat-api-admin", "SeatApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("seat-api-b", "SeatApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      app.playerService.registerPlayer("seat-api-c", "SeatApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      app.playerService.registerPlayer("seat-api-d", "SeatApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )
    val stage = TournamentStage(IdGenerator.stageId(), "Seat API Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Seat API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head

    withServer(app) { baseUrl =>
      val disconnectedSeat = table.seats(0)
      val disconnectResponse = postJson(
        s"$baseUrl/tables/${table.id.value}/seats/${disconnectedSeat.seat.toString}/state",
        write(UpdateTableSeatStateRequest(disconnectedSeat.playerId.value, disconnected = Some(true), note = Some("wifi down")))
      )
      assertEquals(disconnectResponse.statusCode(), 200)
      assert(read[Table](disconnectResponse.body()).seats.find(_.seat == disconnectedSeat.seat).exists(_.disconnected))

      table.seats.tail.foreach { seat =>
        val readyResponse = postJson(
          s"$baseUrl/tables/${table.id.value}/seats/${seat.seat.toString}/state",
          write(UpdateTableSeatStateRequest(seat.playerId.value, ready = Some(true)))
        )
        assertEquals(readyResponse.statusCode(), 200)
      }

      val stillBlocked = postJson(
        s"$baseUrl/tables/${table.id.value}/start",
        write(OperatorRequest(Some(admin.id.value)))
      )
      assertEquals(stillBlocked.statusCode(), 400)

      val reconnectResponse = postJson(
        s"$baseUrl/tables/${table.id.value}/seats/${disconnectedSeat.seat.toString}/state",
        write(UpdateTableSeatStateRequest(disconnectedSeat.playerId.value, disconnected = Some(false)))
      )
      assertEquals(reconnectResponse.statusCode(), 200)
      val finalReady = postJson(
        s"$baseUrl/tables/${table.id.value}/seats/${disconnectedSeat.seat.toString}/state",
        write(UpdateTableSeatStateRequest(disconnectedSeat.playerId.value, ready = Some(true)))
      )
      assertEquals(finalReady.statusCode(), 200)

      val started = postJson(
        s"$baseUrl/tables/${table.id.value}/start",
        write(OperatorRequest(Some(admin.id.value)))
      )
      assertEquals(started.statusCode(), 200)
      val startedTable = read[Table](started.body())
      assertEquals(startedTable.status, TableStatus.InProgress)
      assert(startedTable.seats.forall(_.ready))
      assert(!startedTable.seats.exists(_.disconnected))
    }
  }


  test("club honor endpoints award and revoke honors") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:20:00Z")

    val owner = app.playerService.registerPlayer("api-honor-owner", "ApiHonorOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = app.clubService.createClub("API Honor Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val awardResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/honors",
        write(AwardClubHonorRequest(owner.id.value, "Golden Tile", Some("season MVP"), Some(now.plusSeconds(60))))
      )
      assertEquals(awardResponse.statusCode(), 200)
      assertEquals(read[Club](awardResponse.body()).honors.map(_.title), Vector("Golden Tile"))

      val revokeResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/honors/revoke",
        write(RevokeClubHonorRequest(owner.id.value, "Golden Tile", Some("retired award")))
      )
      assertEquals(revokeResponse.statusCode(), 200)
      assertEquals(read[Club](revokeResponse.body()).honors, Vector.empty)
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

