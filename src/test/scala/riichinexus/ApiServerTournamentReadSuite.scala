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
import riichinexus.microservices.auth.objects.apiTypes.CreateGuestSessionRequest
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

class ApiServerTournamentReadSuite extends FunSuite with ApiServerSuiteSupport:

  test("tournament whitelist and stage tables endpoints return seeded tournament data") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T09:00:00Z")

    val admin = playerService(app).registerPlayer("admin-1", "Admin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("p2", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("p3", "Charlie", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      playerService(app).registerPlayer("p4", "Delta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    )

    val club = clubApi(app).createClub("Whitelist Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player =>
      clubApi(app).addMember(club.id, player.id, principalFor(app, admin.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Swiss Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Whitelist Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    tournamentService(app).whitelistClub(tournament.id, club.id, principalFor(app, admin.id))
    players.foreach(player =>
      tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val whitelistResponse = postApi(
        baseUrl,
        TournamentWhitelistListAPIMessage(tournament.id.value)
      )
      assertEquals(whitelistResponse.statusCode(), 200)
      val whitelist = readPage[TournamentWhitelistEntryView](whitelistResponse.body())
      assert(whitelist.items.exists(_.clubId.contains(club.id)))
      assertEquals(
        whitelist.items.count(_.participantKind == TournamentParticipantKind.Player),
        players.size
      )

      val tablesResponse = postApi(
        baseUrl,
        TournamentStageTablesAPIMessage(tournament.id.value, stage.id.value)
      )
      assertEquals(tablesResponse.statusCode(), 200)
      val tables = readPage[TournamentTableView](tablesResponse.body())
      assertEquals(tables.total, 1)
      assertEquals(tables.items.head.seats.map(_.playerId).toSet, players.map(_.id).toSet)
    }
  }
