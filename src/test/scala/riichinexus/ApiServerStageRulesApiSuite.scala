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
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.dictionary.api.*
import riichinexus.microservices.dictionary.objects.apiTypes.UpsertDictionaryRequest
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.system.objects.apiTypes.OperatorRequest
import riichinexus.system.objects.apiTypes.OperatorRequest.given
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicQueryResponses.given
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import upickle.default.*

class ApiServerStageRulesApiSuite extends FunSuite with ApiServerSuiteSupport:
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
      val stageTablesResponse = postApi(
        baseUrl,
        TournamentStageTablesAPIMessage(tournament.id.value, stage.id.value, roundNumber = Some(2))
      )
      assertEquals(stageTablesResponse.statusCode(), 200)
      val stageTables = readPage[TournamentTableView](stageTablesResponse.body())
      assertEquals(stageTables.total, 1)
      assertEquals(stageTables.items.head.stageRoundNumber, 2)

      val globalTablesResponse = postApi(
        baseUrl,
        TournamentTableListAPIMessage(tournamentId = Some(tournament.id.value), roundNumber = Some(2))
      )
      assertEquals(globalTablesResponse.statusCode(), 200)
      val globalTables = readPage[TournamentTableView](globalTablesResponse.body())
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
      val dictionaryResponse = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(
          operatorId = superAdmin.id.value,
          key = "tournament.rule-template.swiss-snake-template",
          value = "advancement=SwissCut;cutSize=8;pairingMethod=snake;maxRounds=2;schedulingPoolSize=2;note=template backed",
          note = Some("template seed")
        )
      )
      assertEquals(dictionaryResponse.statusCode(), 201)

      val rulesResponse = postApi(
        baseUrl,
        TournamentStageConfigureRulesAPIMessage(
          tournament.id.value,
          stage.id.value,
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = None,
            schedulingPoolSize = None,
            ruleTemplateKey = Some("swiss-snake-template")
          )
        )
      )
      assertEquals(rulesResponse.statusCode(), 200)
      read[TournamentSummaryView](rulesResponse.body())
      val updatedStage = tournamentRepository(app).findById(tournament.id).getOrElse(fail("missing tournament")).stages.head
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
      val rulesResponse = postApi(
        baseUrl,
        TournamentStageConfigureRulesAPIMessage(
          tournament.id.value,
          stage.id.value,
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = Some("SwissCut"),
            cutSize = Some(8),
            pairingMethod = Some("snake")
          )
        )
      )
      assertEquals(rulesResponse.statusCode(), 200)
      read[TournamentSummaryView](rulesResponse.body())
      assertEquals(
        tournamentRepository(app).findById(tournament.id).getOrElse(fail("missing tournament")).stages.head.swissRule.map(_.pairingMethod),
        Some("snake")
      )

      val scheduleResponse = postApi(
        baseUrl,
        TournamentStageScheduleTablesAPIMessage(tournament.id.value, stage.id.value, Some(admin.id.value))
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
      val rulesResponse = postApi(
        baseUrl,
        TournamentStageConfigureRulesAPIMessage(
          tournament.id.value,
          stage.id.value,
          ConfigureStageRulesRequest(
            operatorId = admin.id.value,
            advancementRuleType = Some("Custom"),
            targetTableCount = Some(1),
            note = Some("top=4")
          )
        )
      )
      assertEquals(rulesResponse.statusCode(), 200)

      val scheduleResponse = postApi(
        baseUrl,
        TournamentStageScheduleTablesAPIMessage(tournament.id.value, stage.id.value, Some(admin.id.value))
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
      val response = postApi(
        baseUrl,
        TournamentStageStandingsAPIMessage(tournament.id.value, stage.id.value)
      )
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
