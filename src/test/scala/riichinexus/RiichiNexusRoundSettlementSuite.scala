package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusRoundSettlementSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("round settlement validation and match record notes are populated from paifu") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T19:30:00Z")

    val players = Vector(
      playerService(app).registerPlayer("settle-a", "SettleA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("settle-b", "SettleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("settle-c", "SettleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      playerService(app).registerPlayer("settle-d", "SettleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    def prepareTable(label: String, offset: Long): (Tournament, TournamentStage, Table) =
      val stage = TournamentStage(IdGenerator.stageId(), s"$label Stage", StageFormat.Swiss, 1, 1)
      val tournament = tournamentService(app).createTournament(
        s"$label Cup",
        "QA",
        now.plusSeconds(offset),
        now.plusSeconds(offset + 7200),
        Vector(stage)
      )
      players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
      tournamentService(app).publishTournament(tournament.id)
      val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
      tableService(app).startTable(table.id, now.plusSeconds(offset + 60))
      (tournament, stage, table)

    val (validTournament, validStage, validTable) = prepareTable("Settlement Valid", 0)
    val basePaifu = demoPaifuForResult(
      validTable,
      validTournament.id,
      validStage.id,
      now.plusSeconds(120),
      winner = validTable.seats.head.playerId,
      target = validTable.seats(1).playerId
    )
    val validPaifu = basePaifu.copy(
      rounds = Vector(
        basePaifu.rounds.head.copy(
          descriptor = KyokuDescriptor(SeatWind.East, 1, honba = 1),
          result = basePaifu.rounds.head.result.copy(
            settlement = Some(
              RoundSettlement(
                riichiSticksDelta = 1000,
                honbaPayment = 300,
                notes = Vector("riichi sticks awarded")
              )
            )
          )
        )
      )
    )

    tableService(app).recordCompletedTable(validTable.id, validPaifu)
    val record = matchRecordRepository(app).findByTable(validTable.id).getOrElse(fail("record missing"))
    assert(record.notes.exists(_.contains("settlement riichi=1000 honba=300")))

    val (invalidTournament, invalidStage, invalidTable) = prepareTable("Settlement Invalid", 8000)
    val invalidBasePaifu = demoPaifuForResult(
      invalidTable,
      invalidTournament.id,
      invalidStage.id,
      now.plusSeconds(8120),
      winner = invalidTable.seats.head.playerId,
      target = invalidTable.seats(1).playerId
    )
    val invalidPaifu = invalidBasePaifu.copy(
      rounds = Vector(
        invalidBasePaifu.rounds.head.copy(
          descriptor = KyokuDescriptor(SeatWind.East, 1),
          actions = invalidBasePaifu.rounds.head.actions.filterNot(_.actionType == PaifuActionType.Riichi),
          result = invalidBasePaifu.rounds.head.result.copy(
            settlement = Some(RoundSettlement(riichiSticksDelta = 1000, honbaPayment = 0))
          )
        )
      )
    )

    intercept[IllegalArgumentException] {
      tableService(app).recordCompletedTable(invalidTable.id, invalidPaifu)
    }
  }
