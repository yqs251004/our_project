package riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout

import java.time.Instant

import munit.FunSuite

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.recordmanagement.model.{MatchRecord, MatchRecordSeatResult}
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.{Tournament, TournamentStage}
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.{KnockoutLane, KnockoutRuleConfig}
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageStandingEntry
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType, StageAdvancementSnapshot}
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableSeat, TableStatus, TableId}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentFormat, TournamentId, TournamentStageId}

class TournamentKnockoutBracketBuilderSuite extends FunSuite:

  test("rating seeded knockout bracket creates championship, bronze and repechage lanes") {
    val players = knockoutPlayers(8)
    val snapshot = TournamentKnockoutBracketBuilder.build(
      tournament = tournament(players),
      stage = knockoutStage("rating", thirdPlaceMatch = true, repechageEnabled = true),
      advancement = advancement(players.reverse.map(_.id), players),
      participants = players,
      tables = Vector.empty,
      records = Vector.empty,
      at = fixedInstant
    )

    val firstRound = snapshot.rounds.find(_.roundNumber == 1).get
    val finalRound = snapshot.rounds.find(round => round.matches.exists(matchNode => matchNode.id == "r2-m1")).get

    assertEquals(snapshot.bracketSize, 8)
    assertEquals(snapshot.qualifiedPlayerIds, players.map(_.id))
    assertEquals(firstRound.matches.map(_.slots.flatMap(_.playerId)), Vector(
      Vector(players(0).id, players(7).id, players(2).id, players(5).id),
      Vector(players(1).id, players(6).id, players(3).id, players(4).id)
    ))
    assert(firstRound.matches.forall(_.unlocked))
    assert(!finalRound.matches.head.unlocked)
    assert(snapshot.rounds.exists(_.matches.exists(_.lane == KnockoutLane.Bronze)))
    assert(snapshot.rounds.exists(_.matches.exists(_.lane == KnockoutLane.Repechage)))
  }

  test("archived first-round knockout records unlock final, bronze and repechage slots") {
    val players = knockoutPlayers(8)
    val stage = knockoutStage("rating", thirdPlaceMatch = true, repechageEnabled = true)
    val tournamentModel = tournament(players)
    val firstMatchPlayers = Vector(players(0).id, players(7).id, players(2).id, players(5).id)
    val secondMatchPlayers = Vector(players(1).id, players(6).id, players(3).id, players(4).id)
    val firstRecord = matchRecord(
      id = "first",
      tableId = TableId("table-r1-m1"),
      results = Vector(firstMatchPlayers(2), firstMatchPlayers(0), firstMatchPlayers(3), firstMatchPlayers(1))
    )
    val secondRecord = matchRecord(
      id = "second",
      tableId = TableId("table-r1-m2"),
      results = Vector(secondMatchPlayers(0), secondMatchPlayers(3), secondMatchPlayers(1), secondMatchPlayers(2))
    )
    val tables = Vector(
      archivedTable("r1-m1", firstRecord.tableId, firstMatchPlayers, firstRecord.id),
      archivedTable("r1-m2", secondRecord.tableId, secondMatchPlayers, secondRecord.id)
    )

    val snapshot = TournamentKnockoutBracketBuilder.build(
      tournament = tournamentModel,
      stage = stage,
      advancement = advancement(players.map(_.id), players),
      participants = players,
      tables = tables,
      records = Vector(firstRecord, secondRecord),
      at = fixedInstant
    )
    val finalMatch = snapshot.rounds.flatMap(_.matches).find(_.id == "r2-m1").get
    val bronzeMatch = snapshot.rounds.flatMap(_.matches).find(_.id == "bronze-r1-m1").get
    val repechageMatch = snapshot.rounds.flatMap(_.matches).find(_.id == "repechage-r1-m1").get

    assert(finalMatch.unlocked)
    assertEquals(finalMatch.slots.flatMap(_.playerId), Vector(firstMatchPlayers(2), firstMatchPlayers(0), secondMatchPlayers(0), secondMatchPlayers(3)))
    assertEquals(finalMatch.sourceMatchIds, Vector("r1-m1", "r1-m2"))
    assert(bronzeMatch.unlocked)
    assertEquals(bronzeMatch.slots.flatMap(_.playerId), Vector(firstMatchPlayers(3), firstMatchPlayers(1), secondMatchPlayers(1), secondMatchPlayers(2)))
    assert(repechageMatch.unlocked)
    assertEquals(repechageMatch.slots.flatMap(_.playerId), bronzeMatch.slots.flatMap(_.playerId))
  }

  test("ranking seeded knockout bracket honors explicit standing seeds") {
    val players = knockoutPlayers(4)
    val seededOrder = Vector(players(3).id, players(0).id, players(2).id, players(1).id)
    val snapshot = TournamentKnockoutBracketBuilder.build(
      tournament = tournament(players),
      stage = knockoutStage("standings", thirdPlaceMatch = false, repechageEnabled = false),
      advancement = advancement(seededOrder, players),
      participants = players,
      tables = Vector.empty,
      records = Vector.empty,
      at = fixedInstant
    )

    assertEquals(snapshot.qualifiedPlayerIds, seededOrder)
    assertEquals(snapshot.rounds.head.matches.head.slots.flatMap(_.playerId), Vector(seededOrder(0), seededOrder(3), seededOrder(1), seededOrder(2)))
    assert(snapshot.summary.contains("ranking"))
  }

  test("rating seeded 64-player knockout bracket creates sixteen first-round tables") {
    val players = knockoutPlayers(64)
    val snapshot = TournamentKnockoutBracketBuilder.build(
      tournament = tournament(players),
      stage = knockoutStage("rating", thirdPlaceMatch = false, repechageEnabled = false),
      advancement = advancement(players.map(_.id), players),
      participants = players,
      tables = Vector.empty,
      records = Vector.empty,
      at = fixedInstant
    )
    val firstRound = snapshot.rounds.find(_.roundNumber == 1).get
    val finalMatch = snapshot.rounds.last.matches.head

    assertEquals(snapshot.bracketSize, 64)
    assertEquals(snapshot.rounds.size, 5)
    assertEquals(firstRound.matches.size, 16)
    assert(firstRound.matches.forall(_.unlocked))
    assertEquals(firstRound.matches.flatMap(_.slots.flatMap(_.playerId)).toSet, players.map(_.id).toSet)
    assert(!finalMatch.unlocked)
  }

  test("knockout bracket rejects non power-of-two riichi table sizes") {
    val players = knockoutPlayers(6)

    intercept[IllegalArgumentException] {
      TournamentKnockoutBracketBuilder.build(
        tournament = tournament(players),
        stage = knockoutStage("rating", thirdPlaceMatch = false, repechageEnabled = false),
        advancement = advancement(players.map(_.id), players),
        participants = players,
        tables = Vector.empty,
        records = Vector.empty,
        at = fixedInstant
      )
    }
  }

  private val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")
  private val tournamentId = TournamentId("tournament-knockout-test")
  private val stageId = TournamentStageId("stage-knockout-test")

  private def knockoutPlayers(count: Int): Vector[Player] =
    (1 to count).toVector.map { index =>
      Player(
        id = PlayerId(s"knockout-player-$index"),
        userId = s"knockout-user-$index",
        nickname = f"Knockout Player $index%02d",
        registeredAt = fixedInstant,
        currentRank = RankSnapshot(RankPlatform.MahjongSoul, "Novice"),
        elo = 2300 - index * 20
      )
    }

  private def tournament(players: Vector[Player]): Tournament =
    Tournament(
      id = tournamentId,
      name = "Knockout Test Tournament",
      organizer = "test",
      startsAt = fixedInstant,
      endsAt = fixedInstant.plusSeconds(3600),
      participatingPlayers = players.map(_.id)
    )

  private def knockoutStage(
      seedingPolicy: String,
      thirdPlaceMatch: Boolean,
      repechageEnabled: Boolean
  ): TournamentStage =
    TournamentStage(
      id = stageId,
      name = "Knockout Test Stage",
      format = TournamentFormat.Knockout,
      order = 2,
      roundCount = 2,
      advancementRule = AdvancementRule(AdvancementRuleType.KnockoutElimination),
      knockoutRule = Some(
        KnockoutRuleConfig(
          seedingPolicy = seedingPolicy,
          thirdPlaceMatch = thirdPlaceMatch,
          repechageEnabled = repechageEnabled
        )
      )
    )

  private def advancement(qualifiedPlayerIds: Vector[PlayerId], participants: Vector[Player]): StageAdvancementSnapshot =
    val qualifiedSet = qualifiedPlayerIds.toSet
    val standings = qualifiedPlayerIds.zipWithIndex.map { case (playerId, index) =>
      val participant = participants.find(_.id == playerId).get
      StageStandingEntry(
        playerId = playerId,
        matchesPlayed = 3,
        placementPoints = 12 - index,
        totalScoreDelta = participant.elo,
        totalFinalPoints = 25000 + participant.elo,
        averagePlacement = index + 1.0,
        qualified = qualifiedSet.contains(playerId),
        seed = Some(index + 1)
      )
    }

    StageAdvancementSnapshot(
      tournamentId = tournamentId,
      stageId = stageId,
      generatedAt = fixedInstant,
      rule = AdvancementRule(AdvancementRuleType.KnockoutElimination),
      standings = standings,
      qualifiedPlayerIds = qualifiedPlayerIds,
      summary = "test advancement"
    )

  private def archivedTable(
      matchId: String,
      tableId: TableId,
      players: Vector[PlayerId],
      recordId: MatchRecordId
  ): Table =
    Table(
      id = tableId,
      tableNo = matchId.last.asDigit,
      tournamentId = tournamentId,
      stageId = stageId,
      seats = SeatWind.all.zip(players).map { case (wind, playerId) =>
        TableSeat(wind, playerId)
      },
      stageRoundNumber = 1,
      bracketMatchId = Some(matchId),
      bracketRoundNumber = Some(1),
      status = TableStatus.Archived,
      matchRecordId = Some(recordId)
    )

  private def matchRecord(
      id: String,
      tableId: TableId,
      results: Vector[PlayerId]
  ): MatchRecord =
    MatchRecord(
      id = MatchRecordId(s"record-knockout-$id"),
      tableId = tableId,
      tournamentId = tournamentId,
      stageId = stageId,
      stageRoundNumber = 1,
      generatedAt = fixedInstant,
      seatResults = SeatWind.all.zip(results).zipWithIndex.map { case ((wind, playerId), index) =>
        MatchRecordSeatResult(
          playerId = playerId,
          seat = wind,
          finalPoints = 45000 - index * 10000,
          placement = index + 1,
          scoreDelta = 20000 - index * 10000
        )
      }
    )
