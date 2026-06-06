package riichinexus.microservices.tournament.domain.tablemanagement.functions

import java.time.Instant

import munit.FunSuite

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.{Tournament, TournamentStage}
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentFormat, TournamentId, TournamentStageId}

class TournamentStageFormatPlanningSuite extends FunSuite:

  test("round robin planning rotates pods between rounds without dropping players") {
    val players = formatPlayers(8)
    val roundRobinStage = tournamentStage("round-robin", TournamentFormat.RoundRobin)

    val roundOnePlans = TournamentStageTableScheduler.buildRoundRobinTables(players, roundRobinStage, roundNumber = 1)
    val roundTwoPlans = TournamentStageTableScheduler.buildRoundRobinTables(players, roundRobinStage, roundNumber = 2)
    val roundOneGroups = tablePlayerSets(roundOnePlans)
    val roundTwoGroups = tablePlayerSets(roundTwoPlans)

    assertEquals(roundOnePlans.size, 2)
    assertEquals(roundTwoPlans.size, 2)
    assertEquals(roundOnePlans.flatMap(_.seats.map(_.playerId)).toSet, players.map(_.id).toSet)
    assertEquals(roundTwoPlans.flatMap(_.seats.map(_.playerId)).toSet, players.map(_.id).toSet)
    assert(roundOneGroups.intersect(roundTwoGroups).isEmpty)
    assertEquals(roundTwoPlans.map(_.roundNumber).distinct, Vector(2))
  }

  test("custom stage planning selects target table count and rotates seeded participants") {
    val players = formatPlayers(12)
    val tournament = Tournament(
      id = TournamentId("tournament-custom-format"),
      name = "Custom Format Test",
      organizer = "test",
      startsAt = fixedInstant,
      endsAt = fixedInstant.plusSeconds(3600),
      participatingPlayers = players.map(_.id)
    )
    val customStage = tournamentStage("custom", TournamentFormat.Custom).copy(
      advancementRule = AdvancementRule(
        AdvancementRuleType.Custom,
        targetTableCount = Some(2),
        note = Some("targetTables=2")
      )
    )

    val roundOnePlayers = TournamentStageTableScheduler.selectCustomStageParticipants(
      tournament,
      customStage,
      players,
      history = Vector.empty,
      roundNumber = 1
    )
    val roundTwoPlayers = TournamentStageTableScheduler.selectCustomStageParticipants(
      tournament,
      customStage,
      players,
      history = Vector.empty,
      roundNumber = 2
    )

    assertEquals(TournamentStageTableScheduler.customStageTableCount(customStage, players.size), 2)
    assertEquals(roundOnePlayers.map(_.id), players.take(8).map(_.id))
    assertEquals(roundTwoPlayers.map(_.id), (players.drop(1) ++ players.take(1)).take(8).map(_.id))
    assertEquals(roundOnePlayers.size, 8)
    assertEquals(roundTwoPlayers.size, 8)
  }

  test("custom stage planning rejects impossible target table counts") {
    val players = formatPlayers(8)
    val oversizedStage = tournamentStage("custom-oversized", TournamentFormat.Custom).copy(
      advancementRule = AdvancementRule(
        AdvancementRuleType.Custom,
        targetTableCount = Some(3),
        note = Some("targetTables=3")
      )
    )

    intercept[IllegalArgumentException] {
      TournamentStageTableScheduler.customStageTableCount(oversizedStage, players.size)
    }
  }

  private val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")

  private def formatPlayers(count: Int): Vector[Player] =
    (1 to count).toVector.map { index =>
      Player(
        id = PlayerId(s"format-player-$index"),
        userId = s"format-user-$index",
        nickname = f"Format Player $index%02d",
        registeredAt = fixedInstant,
        currentRank = RankSnapshot(RankPlatform.MahjongSoul, "Novice"),
        elo = 2500 - index
      )
    }

  private def tournamentStage(id: String, format: TournamentFormat): TournamentStage =
    TournamentStage(
      id = TournamentStageId(s"stage-$id"),
      name = s"Stage $id",
      format = format,
      order = 1,
      roundCount = 4
    )

  private def tablePlayerSets(
      plans: Vector[riichinexus.microservices.tournament.domain.tablemanagement.model.StageTablePlan]
  ): Set[Set[PlayerId]] =
    plans.map(plan => plan.seats.map(_.playerId).toSet).toSet
