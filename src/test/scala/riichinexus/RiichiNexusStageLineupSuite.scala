package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.api.PublicQueryService

class RiichiNexusStageLineupSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("stage lineup preferred winds influence scheduled seats") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T13:00:00Z")

    val owner = playerService(app).registerPlayer("wind-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val south = playerService(app).registerPlayer("wind-south", "South", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val west = playerService(app).registerPlayer("wind-west", "West", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val north = playerService(app).registerPlayer("wind-north", "North", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now)

    val club = clubService(app).createClub("Preferred Wind Club", owner.id, now, owner.asPrincipal)
    Vector(south, west, north).foreach(player =>
      clubService(app).addMember(club.id, player.id, principalFor(app, owner.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Preferred Wind Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Preferred Wind Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    tournamentService(app).registerClub(tournament.id, club.id)
    tournamentService(app).publishTournament(tournament.id)
    tournamentService(app).submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = club.id,
        submittedBy = owner.id,
        submittedAt = now,
        seats = Vector(
          StageLineupSeat(owner.id, preferredWind = Some(SeatWind.East)),
          StageLineupSeat(south.id, preferredWind = Some(SeatWind.South)),
          StageLineupSeat(west.id, preferredWind = Some(SeatWind.West)),
          StageLineupSeat(north.id, preferredWind = Some(SeatWind.North))
        )
      ),
      principalFor(app, owner.id)
    )

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    val seatByPlayer = table.seats.map(seat => seat.playerId -> seat.seat).toMap

    assertEquals(seatByPlayer(owner.id), SeatWind.East)
    assertEquals(seatByPlayer(south.id), SeatWind.South)
    assertEquals(seatByPlayer(west.id), SeatWind.West)
    assertEquals(seatByPlayer(north.id), SeatWind.North)
  }

  test("reserve lineup seats backfill unavailable active players during scheduling") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T16:00:00Z")

    val owner = playerService(app).registerPlayer("reserve-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val alpha = playerService(app).registerPlayer("reserve-alpha", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val bravo = playerService(app).registerPlayer("reserve-bravo", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val absent = playerService(app).registerPlayer("reserve-absent", "Absent", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now)
    val reserve = playerService(app).registerPlayer("reserve-bench", "Reserve", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1550)

    val club = clubService(app).createClub("Reserve Club", owner.id, now, owner.asPrincipal)
    Vector(alpha, bravo, absent, reserve).foreach(player =>
      clubService(app).addMember(club.id, player.id, principalFor(app, owner.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Reserve Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Reserve Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    tournamentService(app).registerClub(tournament.id, club.id)
    tournamentService(app).publishTournament(tournament.id)
    tournamentService(app).submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = club.id,
        submittedBy = owner.id,
        submittedAt = now,
        seats = Vector(
          StageLineupSeat(owner.id),
          StageLineupSeat(alpha.id),
          StageLineupSeat(bravo.id),
          StageLineupSeat(absent.id),
          StageLineupSeat(reserve.id, reserve = true)
        )
      ),
      principalFor(app, owner.id)
    )

    val suspendedAbsent = playerRepository(app).findById(absent.id).getOrElse(fail("absent player missing"))
    playerRepository(app).save(suspendedAbsent.copy(status = PlayerStatus.Suspended))

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    assert(table.seats.exists(_.playerId == reserve.id))
    assert(!table.seats.exists(_.playerId == absent.id))
    assert(table.seats.forall(_.clubId.contains(club.id)))
  }
