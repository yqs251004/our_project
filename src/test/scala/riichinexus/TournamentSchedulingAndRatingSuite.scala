package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.opsanalytics.domain.functions.RatingService
import riichinexus.microservices.opsanalytics.domain.model.EloRatingConfig
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.stage.model.{StageLineupSeat, StageLineupSubmission}
import riichinexus.microservices.tournament.domain.matchrecord.model.{MatchRecord, MatchRecordSeatResult}
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.SeatingPolicy
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentFormat, TournamentId, TournamentStageId}

class TournamentSchedulingAndRatingSuite extends FunSuite:

  test("rating service applies zero-sum ELO changes from final standings") {
    val players = Vector(
      player("rating-a", elo = 1500),
      player("rating-b", elo = 1500),
      player("rating-c", elo = 1500),
      player("rating-d", elo = 1500)
    )
    val standings = Vector(
      seatResult(players(0).id, SeatWind.East, placement = 1, scoreDelta = 22000, finalPoints = 47000, uma = 20),
      seatResult(players(1).id, SeatWind.South, placement = 2, scoreDelta = 5000, finalPoints = 30000, uma = 10),
      seatResult(players(2).id, SeatWind.West, placement = 3, scoreDelta = -7000, finalPoints = 18000, uma = -10),
      seatResult(players(3).id, SeatWind.North, placement = 4, scoreDelta = -20000, finalPoints = 5000, uma = -20)
    )

    val deltas = RatingService.calculateDeltas(players, standings)
    val deltaByPlayer = deltas.map(change => change.playerId -> change.delta).toMap

    assertEquals(deltas.map(_.delta).sum, 0)
    assert(deltaByPlayer(players(0).id) > 0)
    assert(deltaByPlayer(players(1).id) > 0)
    assert(deltaByPlayer(players(2).id) < 0)
    assert(deltaByPlayer(players(3).id) < 0)
  }

  test("rating service rejects mismatched participants and invalid configuration") {
    val players = Vector(
      player("rating-input-a", elo = 1600),
      player("rating-input-b", elo = 1500),
      player("rating-input-c", elo = 1400),
      player("rating-input-d", elo = 1300)
    )
    val standings = players.zip(SeatWind.all).zipWithIndex.map { case ((player, wind), index) =>
      seatResult(player.id, wind, placement = index + 1, scoreDelta = 10000 - index * 7000)
    }

    intercept[IllegalArgumentException] {
      RatingService.calculateDeltas(players.dropRight(1), standings)
    }
    intercept[IllegalArgumentException] {
      RatingService.calculateDeltas(players, standings, EloRatingConfig(kFactor = 0))
    }
    intercept[IllegalArgumentException] {
      RatingService.calculateDeltas(players, standings, EloRatingConfig(placementWeight = 0.8, scoreWeight = 0.3, umaWeight = 0.1))
    }
  }

  test("snake swiss pairing schedules multiple tables without duplicate seats") {
    val players = (1 to 8).toVector.map(index => player(s"snake-$index", elo = 2100 - index))
    val plans = SeatingPolicy.planTables(
      players = players,
      stage = swissStage("snake-stage", pairingMethod = "snake"),
      roundNumber = 3
    )

    assertEquals(plans.size, 2)
    assertEquals(plans.map(_.roundNumber).distinct, Vector(3))
    assertEquals(plans.flatMap(_.seats.map(_.playerId)).toSet, players.map(_.id).toSet)
    assertEquals(plans.find(_.tableNo == 1).get.seats.map(_.playerId).toSet, Set(players(0).id, players(3).id, players(4).id, players(7).id))
    assertEquals(plans.find(_.tableNo == 2).get.seats.map(_.playerId).toSet, Set(players(1).id, players(2).id, players(5).id, players(6).id))
  }

  test("balanced swiss pairing spreads multi-club lineups across tables") {
    val clubA = ClubId("club-balanced-a")
    val clubB = ClubId("club-balanced-b")
    val clubC = ClubId("club-balanced-c")
    val clubD = ClubId("club-balanced-d")
    val clubPlayers = Vector(
      clubA -> Vector(player("balanced-a1", 2120, Some(clubA)), player("balanced-a2", 2080, Some(clubA))),
      clubB -> Vector(player("balanced-b1", 2110, Some(clubB)), player("balanced-b2", 2070, Some(clubB))),
      clubC -> Vector(player("balanced-c1", 2100, Some(clubC)), player("balanced-c2", 2060, Some(clubC))),
      clubD -> Vector(player("balanced-d1", 2090, Some(clubD)), player("balanced-d2", 2050, Some(clubD)))
    )
    val players = clubPlayers.flatMap(_._2)
    val stage = swissStage("balanced-stage", pairingMethod = "balanced-elo").copy(
      lineupSubmissions = clubPlayers.map { case (clubId, members) => lineup(clubId, members) }
    )

    val plans = SeatingPolicy.planTables(players, stage, roundNumber = 2)

    assertEquals(plans.size, 2)
    assert(plans.forall(plan => plan.seats.flatMap(_.clubId).distinct.size == 4))
    assertEquals(plans.flatMap(_.seats.map(_.playerId)).toSet, players.map(_.id).toSet)
  }

  test("balanced swiss pairing handles 64 players across 8 clubs") {
    val clubs = (1 to 8).toVector.map(index => ClubId(s"club-large-$index"))
    val players = clubs.zipWithIndex.flatMap { case (clubId, clubIndex) =>
      (1 to 8).toVector.map { playerIndex =>
        player(
          s"large-${clubIndex + 1}-$playerIndex",
          elo = 2400 - clubIndex * 20 - playerIndex,
          clubId = Some(clubId)
        )
      }
    }
    val stage = swissStage("large-balanced-stage", pairingMethod = "balanced-elo")

    val plans = SeatingPolicy.planTables(players, stage, roundNumber = 1)

    assertEquals(plans.size, 16)
    assertEquals(plans.flatMap(_.seats.map(_.playerId)).toSet, players.map(_.id).toSet)
    assert(plans.forall(plan => plan.seats.map(_.playerId).distinct.size == 4))
    assert(plans.forall(plan => plan.seats.flatMap(_.clubId).distinct.size == 4))
  }

  test("balanced swiss pairing respects alliance and rivalry club relations") {
    val clubs = (1 to 8).toVector.map(index => ClubId(s"club-relation-$index"))
    val players = clubs.zipWithIndex.flatMap { case (clubId, clubIndex) =>
      (1 to 2).toVector.map { playerIndex =>
        player(
          s"relation-${clubIndex + 1}-$playerIndex",
          elo = 2200 - clubIndex * 10 - playerIndex,
          clubId = Some(clubId)
        )
      }
    }
    val clubRelations = Map(
      clubPair(clubs(0), clubs(1)) -> ClubRelationKind.Alliance,
      clubPair(clubs(2), clubs(3)) -> ClubRelationKind.Rivalry,
      clubPair(clubs(4), clubs(5)) -> ClubRelationKind.Neutral
    )

    val plans = SeatingPolicy.planTables(
      players,
      swissStage("relation-stage", pairingMethod = "balanced-elo"),
      roundNumber = 1,
      clubRelations = clubRelations
    )
    val tableClubSets = plans.map(plan => plan.seats.flatMap(_.clubId).toSet)

    assertEquals(plans.size, 4)
    assertEquals(plans.flatMap(_.seats.map(_.playerId)).toSet, players.map(_.id).toSet)
    assert(!tableClubSets.exists(clubsAtTable => clubsAtTable.contains(clubs(0)) && clubsAtTable.contains(clubs(1))))
    assert(tableClubSets.exists(clubsAtTable => clubsAtTable.contains(clubs(2)) && clubsAtTable.contains(clubs(3))))
    assert(plans.forall(plan => plan.seats.flatMap(_.clubId).distinct.size == 4))
  }

  test("balanced swiss pairing uses representative club over a player's primary club") {
    val primaryClub = ClubId("club-primary-binding")
    val representedClub = ClubId("club-stage-representative")
    val players = Vector(
      player("represent-a1", 1900, Some(primaryClub)),
      player("represent-a2", 1890, Some(primaryClub)),
      player("represent-b1", 1880, Some(representedClub)),
      player("represent-c1", 1870, Some(ClubId("club-represent-c")))
    )
    val stage = swissStage("representative-stage", pairingMethod = "balanced-elo").copy(
      lineupSubmissions = Vector(lineup(representedClub, players.take(2)))
    )

    val plan = SeatingPolicy.planTables(players, stage, roundNumber = 1).head
    val clubByPlayer = plan.seats.map(seat => seat.playerId -> seat.clubId).toMap

    assertEquals(clubByPlayer(players(0).id), Some(representedClub))
    assertEquals(clubByPlayer(players(1).id), Some(representedClub))
    assertEquals(clubByPlayer(players(2).id), Some(representedClub))
  }

  test("balanced swiss pairing avoids repeating a complete previous table when alternatives exist") {
    val players = (1 to 8).toVector.map(index => player(s"rematch-$index", elo = 2200 - index * 10))
    val previousTable = players.take(4).map(_.id).toSet
    val historicalRecord = matchRecord("rematch-history", players.take(4).map(_.id))

    val plans = SeatingPolicy.planTables(
      players,
      swissStage("rematch-stage", pairingMethod = "balanced-elo"),
      roundNumber = 2,
      historicalRecords = Vector(historicalRecord)
    )

    assertEquals(plans.size, 2)
    assert(!plans.exists(plan => plan.seats.map(_.playerId).toSet == previousTable))
  }

  test("lineup preferred winds are honored when a table can satisfy them") {
    val clubId = ClubId("club-preferred-winds")
    val players = Vector(
      player("wind-east", 1800, Some(clubId)),
      player("wind-south", 1790, Some(clubId)),
      player("wind-west", 1780, Some(clubId)),
      player("wind-north", 1770, Some(clubId))
    )
    val stage = swissStage("preferred-wind-stage", pairingMethod = "balanced-elo").copy(
      lineupSubmissions = Vector(
        StageLineupSubmission(
          id = LineupSubmissionId("lineup-preferred-winds"),
          clubId = clubId,
          submittedBy = players.head.id,
          submittedAt = fixedInstant,
          seats = SeatWind.all.zip(players).map { case (wind, player) =>
            StageLineupSeat(player.id, preferredWind = Some(wind))
          }
        )
      )
    )

    val plan = SeatingPolicy.planTables(players, stage, roundNumber = 1).head
    val seatByPlayer = plan.seats.map(seat => seat.playerId -> seat.seat).toMap

    assertEquals(seatByPlayer(players(0).id), SeatWind.East)
    assertEquals(seatByPlayer(players(1).id), SeatWind.South)
    assertEquals(seatByPlayer(players(2).id), SeatWind.West)
    assertEquals(seatByPlayer(players(3).id), SeatWind.North)
  }

  test("seating policy rejects invalid player counts and conflicting club representation") {
    val players = (1 to 5).toVector.map(index => player(s"invalid-$index", elo = 1600 + index))
    val sharedPlayer = players.head
    val conflictedStage = swissStage("conflicted-stage", pairingMethod = "balanced-elo").copy(
      lineupSubmissions = Vector(
        lineup(ClubId("club-conflict-a"), Vector(sharedPlayer)),
        lineup(ClubId("club-conflict-b"), Vector(sharedPlayer))
      )
    )

    intercept[IllegalArgumentException] {
      SeatingPolicy.planTables(players, swissStage("invalid-count-stage", pairingMethod = "balanced-elo"), roundNumber = 1)
    }
    intercept[IllegalArgumentException] {
      SeatingPolicy.planTables(players.take(4), conflictedStage, roundNumber = 1)
    }
  }

  private val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")

  private def player(id: String, elo: Int, clubId: Option[ClubId] = None): Player =
    Player(
      id = PlayerId(s"player-$id"),
      userId = s"user-$id",
      nickname = s"Player $id",
      registeredAt = fixedInstant,
      currentRank = RankSnapshot(RankPlatform.MahjongSoul, "Novice"),
      elo = elo,
      clubId = clubId
    )

  private def swissStage(id: String, pairingMethod: String): TournamentStage =
    TournamentStage(
      id = TournamentStageId(id),
      name = id,
      format = TournamentFormat.Swiss,
      order = 1,
      roundCount = 4,
      swissRule = Some(SwissRuleConfig(pairingMethod = pairingMethod))
    )

  private def lineup(clubId: ClubId, players: Vector[Player]): StageLineupSubmission =
    StageLineupSubmission(
      id = LineupSubmissionId(s"lineup-${clubId.value}"),
      clubId = clubId,
      submittedBy = players.head.id,
      submittedAt = fixedInstant,
      seats = players.map(player => StageLineupSeat(player.id))
    )

  private def matchRecord(id: String, players: Vector[PlayerId]): MatchRecord =
    MatchRecord(
      id = MatchRecordId(s"record-$id"),
      tableId = TableId(s"table-$id"),
      tournamentId = TournamentId("tournament-scheduling-test"),
      stageId = TournamentStageId("stage-scheduling-test"),
      stageRoundNumber = 1,
      generatedAt = fixedInstant,
      seatResults = SeatWind.all.zip(players).zipWithIndex.map { case ((wind, playerId), index) =>
        seatResult(playerId, wind, placement = index + 1, scoreDelta = 0)
      }
    )

  private def clubPair(left: ClubId, right: ClubId): (ClubId, ClubId) =
    if left.value <= right.value then (left, right) else (right, left)

  private def seatResult(
      playerId: PlayerId,
      seat: SeatWind,
      placement: Int,
      scoreDelta: Int,
      finalPoints: Int = 25000,
      uma: Double = 0.0
  ): MatchRecordSeatResult =
    MatchRecordSeatResult(
      playerId = playerId,
      seat = seat,
      finalPoints = finalPoints,
      placement = placement,
      scoreDelta = scoreDelta,
      uma = uma
    )
