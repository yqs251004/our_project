package riichinexus

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import munit.FunSuite



import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.club.api.responses.ClubTournamentResponses.given
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.dictionary.api.requests.UpsertDictionaryRequest
import riichinexus.microservices.opsanalytics.api.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.shared.api.requests.OperatorRequest.given
import riichinexus.microservices.publicquery.api.responses.*
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.given
import riichinexus.microservices.tournament.api.responses.*
import riichinexus.microservices.tournament.api.responses.TournamentOperationResponses.given
import riichinexus.microservices.tournament.api.requests.SettlementRequests.given
import riichinexus.microservices.tournament.api.requests.StageRequests.given
import riichinexus.microservices.tournament.api.requests.TableRequests.given
import riichinexus.microservices.tournament.api.requests.*
import upickle.default.*

class ApiServerOperationsSuite extends FunSuite with ApiServerSuiteSupport:
  test("players and clubs list endpoints support shared pagination and filters") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:30:00Z")

    val owner = playerService(app).registerPlayer("page-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val alpha = playerService(app).registerPlayer("page-alpha", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val bravo = playerService(app).registerPlayer("page-bravo", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val suspended = playerService(app).registerPlayer("page-suspended", "Suspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1400)

    val club = clubService(app).createClub("Paged Club", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, alpha.id, principalFor(app, owner.id))
    clubService(app).addMember(club.id, bravo.id, principalFor(app, owner.id))
    clubService(app).addMember(club.id, suspended.id, principalFor(app, owner.id))
    val suspendedMember = playerRepository(app).findById(suspended.id).getOrElse(fail("suspended member missing"))
    playerRepository(app).save(suspendedMember.copy(status = PlayerStatus.Suspended))

    val retiredClub = clubRepository(app).save(
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

  test("tournament list and public schedules endpoints support filtered summaries") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:40:00Z")

    val admin = playerService(app).registerPlayer("schedule-admin", "ScheduleAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1810)
    val otherAdmin = playerService(app).registerPlayer("schedule-other-admin", "ScheduleOtherAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1790)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("schedule-b", "ScheduleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("schedule-c", "ScheduleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      playerService(app).registerPlayer("schedule-d", "ScheduleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    )

    val publishedStage = TournamentStage(IdGenerator.stageId(), "Published Stage", StageFormat.Swiss, 1, 1)
    val publishedTournament = tournamentService(app).createTournament(
      "Schedule Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(publishedStage),
      adminId = Some(admin.id)
    )
    players.foreach(player =>
      tournamentService(app).registerPlayer(publishedTournament.id, player.id, principalFor(app, admin.id))
    )
    tournamentService(app).publishTournament(publishedTournament.id, principalFor(app, admin.id))
    tournamentService(app).scheduleStageTables(publishedTournament.id, publishedStage.id, principalFor(app, admin.id))

    tournamentService(app).createTournament(
      "Draft Cup",
      "QA",
      now.plusSeconds(3600),
      now.plusSeconds(10800),
      Vector(TournamentStage(IdGenerator.stageId(), "Draft Stage", StageFormat.Swiss, 1, 1)),
      adminId = Some(admin.id)
    )
    tournamentService(app).createTournament(
      "Other Organizer Cup",
      "Campus",
      now.plusSeconds(1800),
      now.plusSeconds(9000),
      Vector(TournamentStage(IdGenerator.stageId(), "Other Stage", StageFormat.Swiss, 1, 1)),
      adminId = Some(otherAdmin.id)
    )

    withServer(app) { baseUrl =>
      val tournamentsResponse = get(
        s"$baseUrl/tournaments?adminId=${admin.id.value}&status=InProgress&organizer=QA"
      )
      assertEquals(tournamentsResponse.statusCode(), 200)
      val tournamentsPage = readPage[Tournament](tournamentsResponse.body())
      assertEquals(tournamentsPage.total, 1)
      assertEquals(tournamentsPage.items.map(_.id), Vector(publishedTournament.id))

      val schedulesResponse = get(
        s"$baseUrl/public/schedules?tournamentStatus=InProgress"
      )
      assertEquals(schedulesResponse.statusCode(), 200)
      val schedulesPage = readPage[PublicScheduleView](schedulesResponse.body())
      assertEquals(schedulesPage.total, 1)
      assertEquals(schedulesPage.items.head.tournamentId, publishedTournament.id)
      assertEquals(schedulesPage.items.head.tableCount, 1)
      assertEquals(schedulesPage.items.head.participantCount, 4)
    }
  }

  test("public club directory endpoint supports filtered summaries") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T13:00:00Z")

    val alphaOwner = playerService(app).registerPlayer("alpha-owner", "AlphaOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1820)
    val alphaActive = playerService(app).registerPlayer("alpha-active", "AlphaActive", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1690)
    val alphaSuspended = playerService(app).registerPlayer("alpha-suspended", "AlphaSuspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val betaOwner = playerService(app).registerPlayer("beta-owner", "BetaOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)

    val alphaClub = clubService(app).createClub("Alpha Club", alphaOwner.id, now, alphaOwner.asPrincipal)
    val betaClub = clubService(app).createClub("Beta Club", betaOwner.id, now.plusSeconds(60), betaOwner.asPrincipal)

    clubService(app).addMember(alphaClub.id, alphaActive.id, principalFor(app, alphaOwner.id))
    clubService(app).addMember(alphaClub.id, alphaSuspended.id, principalFor(app, alphaOwner.id))
    val suspendedPlayer = playerRepository(app).findById(alphaSuspended.id).getOrElse(fail("suspended player missing"))
    playerRepository(app).save(suspendedPlayer.copy(status = PlayerStatus.Suspended))

    clubService(app).updateRelation(
      alphaClub.id,
      ClubRelation(betaClub.id, ClubRelationKind.Rivalry, now.plusSeconds(120), Some("league rival")),
      principalFor(app, alphaOwner.id),
      now.plusSeconds(120)
    )

    withServer(app) { baseUrl =>
      val response = get(
        s"$baseUrl/public/clubs?relation=Rivalry&name=Alpha"
      )
      assertEquals(response.statusCode(), 200)
      val page = readPage[PublicClubDirectoryEntry](response.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.map(_.clubId), Vector(alphaClub.id))
      assertEquals(page.items.head.memberCount, 3)
      assertEquals(page.items.head.activeMemberCount, 2)
      assertEquals(page.items.head.rivalryCount, 1)
      assertEquals(page.items.head.strongestRivalClubId, Some(betaClub.id))
      assertEquals(page.appliedFilters("relation"), "Rivalry")
      assertEquals(page.appliedFilters("name"), "Alpha")
    }
  }

  test("tables records and appeals endpoints support filtering and pagination") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:45:00Z")

    val admin = playerService(app).registerPlayer("filter-admin", "FilterAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else playerService(app).registerPlayer(
        s"filter-p$index",
        s"Player$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1500 + index
      )
    }
    val stage = TournamentStage(IdGenerator.stageId(), "Filter Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Filter Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val tables = tournamentService(app).scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    )

    val firstTable = tables.head
    val secondTable = tables.last
    tableService(app).startTable(firstTable.id, now.plusSeconds(60), principalFor(app, admin.id))
    val winner = firstTable.seats.head.playerId
    val target = firstTable.seats(1).playerId
    tableService(app).recordCompletedTable(
      firstTable.id,
      demoPaifuForResult(firstTable, tournament.id, stage.id, now.plusSeconds(120), winner, target),
      principalFor(app, admin.id)
    )

    appealService(app).fileAppeal(
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

    val root = playerService(app).registerPlayer("page-root", "Root", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val admin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminPrincipal = principalFor(app, admin.id)

    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "rank.formula",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now
    )
    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "rank.scale",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now.plusSeconds(2)
    )
    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "stage.schedulingpoolsize",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now.plusSeconds(3)
    )
    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "rank.formula",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(4)
    )
    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "rank.scale",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(5)
    )
    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "stage.schedulingpoolsize",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(6)
    )

    val rankFormula = dictionaryGovernance(app).upsertDictionary(
      key = "rank.formula.current",
      value = "uma+oka-v3",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(10)
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "rank.scale.mode",
      value = "aggressive",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(60)
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "stage.schedulingpoolsize.default",
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
      assertEquals(dictionaryPage.items.head.key, "rank.formula.current")

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

    val root = playerService(app).registerPlayer("dict-api-root", "DictApiRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val admin = playerService(app).registerPlayer("dict-api-admin", "DictApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("dict-api-b", "DictApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("dict-api-c", "DictApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("dict-api-d", "DictApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "API Settlement Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "API Dictionary Settlement Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
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
    tournamentService(app).completeStage(
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

  test("tournament settlement API supports draft revisions finalization and status filters") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:40:00Z")

    val superRoot = playerService(app).registerPlayer("settle-api-root", "SettleApiRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val superAdmin = playerRepository(app).save(superRoot.grantRole(RoleGrant.superAdmin(now)))
    val admin = playerService(app).registerPlayer("settle-api-admin", "SettleApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("settle-api-b", "SettleApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("settle-api-c", "SettleApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("settle-api-d", "SettleApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val club = clubService(app).createClub("Settlement API Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player => clubService(app).addMember(club.id, player.id, principalFor(app, admin.id)))

    val stage = TournamentStage(IdGenerator.stageId(), "Settlement API Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Settlement API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
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
    tournamentService(app).completeStage(
      tournament.id,
      stage.id,
      principalFor(app, admin.id),
      now.plusSeconds(180)
    )

    withServer(app) { baseUrl =>
      val draftResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settle",
        write(
          SettleTournamentRequest(
            operatorId = admin.id.value,
            finalStageId = stage.id.value,
            prizePool = 1000,
            payoutRatios = Vector(0.6, 0.25, 0.15),
            houseFeeAmount = 100,
            clubShareRatio = 0.2,
            adjustments = Vector(
              SettlementAdjustmentRequest(players(1).id.value, "sportsmanship-award", 50L),
              SettlementAdjustmentRequest(players(2).id.value, "late-penalty", -10L)
            ),
            finalizeSettlement = false,
            note = Some("draft payout")
          )
        )
      )
      assertEquals(draftResponse.statusCode(), 200)
      val draft = read[TournamentSettlementSnapshot](draftResponse.body())
      assertEquals(draft.status, TournamentSettlementStatus.Draft)
      assertEquals(draft.revision, 1)
      assertEquals(draft.houseFeeAmount, 100L)
      assertEquals(draft.entries.head.baseAwardAmount, 540L)

      val finalizeResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements/${draft.id.value}/finalize",
        write(FinalizeTournamentSettlementRequest(admin.id.value, Some("finance approved")))
      )
      assertEquals(finalizeResponse.statusCode(), 200)
      val finalized = read[TournamentSettlementSnapshot](finalizeResponse.body())
      assertEquals(finalized.status, TournamentSettlementStatus.Finalized)

      val revisedResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settle",
        write(
          SettleTournamentRequest(
            operatorId = admin.id.value,
            finalStageId = stage.id.value,
            prizePool = 1100,
            payoutRatios = Vector(0.5, 0.3, 0.2),
            houseFeeAmount = 110,
            clubShareRatio = 0.1,
            adjustments = Vector(SettlementAdjustmentRequest(players(3).id.value, "stream-feature-bonus", 40L)),
            finalizeSettlement = true,
            note = Some("revised payout")
          )
        )
      )
      assertEquals(revisedResponse.statusCode(), 200)
      val revised = read[TournamentSettlementSnapshot](revisedResponse.body())
      assertEquals(revised.revision, 2)
      assertEquals(revised.status, TournamentSettlementStatus.Finalized)
      assertEquals(revised.supersedesSettlementId, Some(draft.id))

      val settlementIndex = get(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements?status=Superseded"
      )
      assertEquals(settlementIndex.statusCode(), 200)
      val supersededPage = readPage[TournamentSettlementSnapshot](settlementIndex.body())
      assertEquals(supersededPage.total, 1)
      assertEquals(supersededPage.items.head.id, draft.id)

      val latestResponse = get(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements/${stage.id.value}"
      )
      assertEquals(latestResponse.statusCode(), 200)
      assertEquals(read[TournamentSettlementSnapshot](latestResponse.body()).id, revised.id)

      val auditPage = get(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements?status=Finalized&championId=${revised.championId.value}"
      )
      assertEquals(auditPage.statusCode(), 200)
      assert(readPage[TournamentSettlementSnapshot](auditPage.body()).items.exists(_.id == revised.id))

      val runtimeDictionaryResponse = postJson(
        s"$baseUrl/admin/dictionary",
        write(UpsertDictionaryRequest(superAdmin.id.value, "settlement.defaultPayoutRatios", "0.6,0.25,0.15", Some("runtime payout tuning")))
      )
      assertEquals(runtimeDictionaryResponse.statusCode(), 201)
    }
  }

  test("club management endpoints revoke admin and remove member") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T13:00:00Z")

    val owner = playerService(app).registerPlayer("club-owner", "ClubOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val vice = playerService(app).registerPlayer("club-vice", "ClubVice", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val member = playerService(app).registerPlayer("club-member", "ClubMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)

    val club = clubService(app).createClub("Managed Club", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, vice.id, principalFor(app, owner.id))
    clubService(app).addMember(club.id, member.id, principalFor(app, owner.id))
    clubService(app).assignAdmin(club.id, vice.id, principalFor(app, owner.id))

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
      assertEquals(playerRepository(app).findById(member.id).map(_.boundClubIds), Some(Vector.empty))
    }
  }

  test("tournament admin revoke endpoint removes scoped admin role") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T14:00:00Z")

    val root = playerService(app).registerPlayer("tour-root", "TournamentRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val adminA = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminB = playerService(app).registerPlayer("tour-admin-b", "TournamentB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val stage = TournamentStage(IdGenerator.stageId(), "Admin Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Admin Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage),
      adminId = Some(adminA.id)
    )
    tournamentService(app).assignTournamentAdmin(
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
      val updatedAdmin = playerRepository(app).findById(adminB.id).getOrElse(fail("missing adminB"))
      assert(!updatedAdmin.roleGrants.exists(grant =>
        grant.role == RoleKind.TournamentAdmin && grant.tournamentId.contains(tournament.id)
      ))
    }
  }

  test("club operation endpoints update treasury point pool and rank tree") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:00:00Z")

    val owner = playerService(app).registerPlayer("club-fin-owner", "ClubFinOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubService(app).createClub("Club Finance", owner.id, now, owner.asPrincipal)

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

    val owner = playerService(app).registerPlayer("club-priv-owner", "ClubPrivOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val delegate = playerService(app).registerPlayer("club-priv-delegate", "ClubPrivDelegate", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val club = clubService(app).createClub("Club Privileges", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, delegate.id, principalFor(app, owner.id))

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

    val owner = playerService(app).registerPlayer("api-title-owner", "ApiTitleOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = playerService(app).registerPlayer("api-title-member", "ApiTitleMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubService(app).createClub("API Title Club", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, member.id, principalFor(app, owner.id))

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

    val ownerA = playerService(app).registerPlayer("api-relation-a", "ApiRelationA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val ownerB = playerService(app).registerPlayer("api-relation-b", "ApiRelationB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1780)
    val clubA = clubService(app).createClub("API Alliance A", ownerA.id, now, ownerA.asPrincipal)
    val clubB = clubService(app).createClub("API Alliance B", ownerB.id, now, ownerB.asPrincipal)

    withServer(app) { baseUrl =>
      val allianceResponse = postJson(
        s"$baseUrl/clubs/${clubA.id.value}/relations",
        write(UpdateClubRelationRequest(ownerA.id.value, clubB.id.value, "Alliance", Some("partner")))
      )
      assertEquals(allianceResponse.statusCode(), 200)
      assertEquals(read[Club](allianceResponse.body()).relations.map(_.targetClubId), Vector(clubB.id))
      assertEquals(
        clubRepository(app).findById(clubB.id).map(_.relations.map(_.targetClubId)),
        Some(Vector(clubA.id))
      )

      val neutralResponse = postJson(
        s"$baseUrl/clubs/${clubA.id.value}/relations",
        write(UpdateClubRelationRequest(ownerA.id.value, clubB.id.value, "Neutral", Some("reset")))
      )
      assertEquals(neutralResponse.statusCode(), 200)
      assertEquals(read[Club](neutralResponse.body()).relations, Vector.empty)
      assertEquals(clubRepository(app).findById(clubB.id).map(_.relations), Some(Vector.empty))
    }
  }

  test("stage table endpoints expose round filters for multi-round scheduling") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:30:00Z")

    val admin = playerService(app).registerPlayer("round-admin", "RoundAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else playerService(app).registerPlayer(
        s"round-p$index",
        s"RoundPlayer$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1600 - index
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Round Stage", StageFormat.Swiss, 1, 2, schedulingPoolSize = 1)
    val tournament = tournamentService(app).createTournament(
      "Round Filter Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))

    val firstRoundTable = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(firstRoundTable.id, now.plusSeconds(60), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
      firstRoundTable.id,
      demoPaifuForResult(firstRoundTable, tournament.id, stage.id, now.plusSeconds(120), firstRoundTable.seats.head.playerId, firstRoundTable.seats(1).playerId),
      principalFor(app, admin.id)
    )
    val secondRoundOne = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).last
    tableService(app).startTable(secondRoundOne.id, now.plusSeconds(180), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
      secondRoundOne.id,
      demoPaifuForResult(secondRoundOne, tournament.id, stage.id, now.plusSeconds(240), secondRoundOne.seats.head.playerId, secondRoundOne.seats(1).playerId),
      principalFor(app, admin.id)
    )
    tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))

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


  test("dictionary-backed rule templates can configure stage rules over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:35:00Z")

    val root = playerService(app).registerPlayer("template-root", "TemplateRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val admin = playerService(app).registerPlayer("template-admin", "TemplateAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else playerService(app).registerPlayer(
        s"template-$index",
        s"Template$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1800 - index * 35
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Template Stage", StageFormat.Swiss, 1, 3)
    val tournament = tournamentService(app).createTournament(
      "Template Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val dictionaryResponse = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = superAdmin.id.value,
            key = "tournament.rule-template.swiss-snake-template",
            value = "advancement=SwissCut;cutSize=8;pairingMethod=snake;maxRounds=2;schedulingPoolSize=2;note=template backed",
            note = Some("template seed")
          )
        )
      )
      assertEquals(dictionaryResponse.statusCode(), 201)

      val rulesResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/rules",
        write(
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = None,
            schedulingPoolSize = None,
            ruleTemplateKey = Some("swiss-snake-template")
          )
        )
      )
      assertEquals(rulesResponse.statusCode(), 200)
      val updatedStage = read[Tournament](rulesResponse.body()).stages.head
      assertEquals(updatedStage.advancementRule.cutSize, Some(8))
      assertEquals(updatedStage.swissRule.map(_.pairingMethod), Some("snake"))
      assertEquals(updatedStage.swissRule.flatMap(_.maxRounds), Some(2))
      assertEquals(updatedStage.schedulingPoolSize, 2)
    }
  }


  test("stage rules API can switch swiss pairingMethod to snake") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:45:00Z")

    val admin = playerService(app).registerPlayer("pair-api-admin", "PairApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else playerService(app).registerPlayer(
        s"pair-api-$index",
        s"PairApi$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1800 - index * 40
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Pair API Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Pair API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val rulesResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/rules",
        write(
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = Some("SwissCut"),
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
      val scheduled = read[TournamentMutationView](scheduleResponse.body())
      val tables = scheduled.scheduledTables.sortBy(_.tableNo)
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

    val admin = playerService(app).registerPlayer("custom-api-admin", "CustomApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else playerService(app).registerPlayer(
        s"custom-api-$index",
        s"CustomApi$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1800 - index * 30
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Custom API Stage", StageFormat.Custom, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Custom API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val rulesResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/rules",
        write(
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = Some("Custom"),
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
      val scheduled = read[TournamentMutationView](scheduleResponse.body())
      val tables = scheduled.scheduledTables
      assertEquals(tables.size, 1)
      assertEquals(tables.head.seats.map(_.playerId).toSet, players.take(4).map(_.id).toSet)
    }
  }

  test("stage standings endpoint honors swiss carryOverPoints flag") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T16:00:00Z")

    val admin = playerService(app).registerPlayer("carry-api-admin", "CarryApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("carry-api-b", "CarryApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("carry-api-c", "CarryApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      playerService(app).registerPlayer("carry-api-d", "CarryApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    val stage = TournamentStage(
      IdGenerator.stageId(),
      "Carry API Stage",
      StageFormat.Swiss,
      1,
      2,
      swissRule = Some(SwissRuleConfig(carryOverPoints = false))
    )
    val tournament = tournamentService(app).createTournament(
      "Carry API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))

    val roundOne = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    roundOne.seats.foreach(seat =>
      tableService(app).updateSeatState(roundOne.id, seat.seat, principalFor(app, seat.playerId), ready = Some(true))
    )
    tableService(app).startTable(roundOne.id, now.plusSeconds(60), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
      roundOne.id,
      demoPaifuForResult(roundOne, tournament.id, stage.id, now.plusSeconds(120), roundOne.seats(0).playerId, roundOne.seats(3).playerId),
      principalFor(app, admin.id)
    )

    val roundTwo = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))
      .find(_.stageRoundNumber == 2)
      .getOrElse(fail("round two table missing"))
    roundTwo.seats.foreach(seat =>
      tableService(app).updateSeatState(roundTwo.id, seat.seat, principalFor(app, seat.playerId), ready = Some(true))
    )
    tableService(app).startTable(roundTwo.id, now.plusSeconds(180), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
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

    val admin = playerService(app).registerPlayer("seat-api-admin", "SeatApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("seat-api-b", "SeatApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("seat-api-c", "SeatApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      playerService(app).registerPlayer("seat-api-d", "SeatApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )
    val stage = TournamentStage(IdGenerator.stageId(), "Seat API Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Seat API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head

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

  test("player self-ready endpoint updates only their own seat before start") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T16:40:00Z")

    val admin = playerService(app).registerPlayer("self-ready-admin", "SelfReadyAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1760)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("self-ready-b", "SelfReadyB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("self-ready-c", "SelfReadyC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("self-ready-d", "SelfReadyD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630)
    )
    val outsider = playerService(app).registerPlayer("self-ready-x", "SelfReadyX", RankSnapshot(RankPlatform.Tenhou, "3-dan"), now, 1500)
    val stage = TournamentStage(IdGenerator.stageId(), "Self Ready Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Self Ready Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    val actorSeat = table.seats(1)

    withServer(app) { baseUrl =>
      val readyResponse = postJson(
        s"$baseUrl/tables/${table.id.value}/ready",
        write(UpdateOwnTableReadyStateRequest(actorSeat.playerId.value))
      )
      assertEquals(readyResponse.statusCode(), 200)
      val updatedTable = read[Table](readyResponse.body())
      assert(updatedTable.seats.find(_.seat == actorSeat.seat).exists(_.ready))
      assertEquals(updatedTable.seats.count(_.ready), 1)

      val outsiderResponse = postJson(
        s"$baseUrl/tables/${table.id.value}/ready",
        write(UpdateOwnTableReadyStateRequest(outsider.id.value))
      )
      assertEquals(outsiderResponse.statusCode(), 400)
    }
  }


  test("club honor endpoints award and revoke honors") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:20:00Z")

    val owner = playerService(app).registerPlayer("api-honor-owner", "ApiHonorOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubService(app).createClub("API Honor Club", owner.id, now, owner.asPrincipal)

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

