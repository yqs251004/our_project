package riichinexus.microservices.opsanalytics.domain.functions

import java.time.Instant

import munit.FunSuite

import riichinexus.microservices.opsanalytics.domain.model.EloRatingConfig
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.recordmanagement.model.MatchRecordSeatResult
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

class RatingServiceSuite extends FunSuite:

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

  private val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")

  private def player(id: String, elo: Int): Player =
    Player(
      id = PlayerId(s"player-$id"),
      userId = s"user-$id",
      nickname = s"Player $id",
      registeredAt = fixedInstant,
      currentRank = RankSnapshot(RankPlatform.MahjongSoul, "Novice"),
      elo = elo
    )

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
