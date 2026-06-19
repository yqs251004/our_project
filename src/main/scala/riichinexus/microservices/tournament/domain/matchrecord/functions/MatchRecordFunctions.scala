package riichinexus.microservices.tournament.domain.matchrecord.functions


import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.domain.matchrecord.model.{MatchRecord, MatchRecordSeatResult}
import riichinexus.microservices.tournament.objects.paifu.Paifu

/** MatchRecordFunctions 提供对局记录相关的领域计算、校验和转换函数。 */

private[tournament] object MatchRecordFunctions:
  def validate(record: MatchRecord): Unit =
    require(record.seatResults.size == 4, "Match record must contain four seat results")
    require(record.seatResults.map(_.playerId).distinct.size == 4, "Match record players must be unique")
    require(record.seatResults.map(_.seat).distinct.size == 4, "Match record seats must be unique")
    require(record.seatResults.map(_.placement).distinct.size == 4, "Match record placements must be unique")
    require(record.stageRoundNumber >= 1, "Match record stage round number must be positive")

  def playerIds(record: MatchRecord): Vector[PlayerId] =
    record.seatResults.map(_.playerId)

  def fromTableAndPaifu(
      table: Table,
      paifu: Paifu,
      generatedAt: Instant,
      finalizedBy: Option[PlayerId] = None
  ): MatchRecord =
    val seatMap = table.seats.map(seat => seat.playerId -> seat).toMap
    require(
      paifu.finalStandings.map(_.playerId).toSet == seatMap.keySet,
      "Paifu final standings must match scheduled table players"
    )

    MatchRecord(
      id = TournamentIdGenerator.matchRecordId(),
      tableId = table.id,
      tournamentId = table.tournamentId,
      stageId = table.stageId,
      stageRoundNumber = table.stageRoundNumber,
      generatedAt = generatedAt,
      seatResults = paifu.finalStandings.map { standing =>
        val scheduledSeat = seatMap(standing.playerId)
        MatchRecordSeatResult(
          playerId = standing.playerId,
          seat = standing.seat,
          clubId = scheduledSeat.clubId,
          finalPoints = standing.finalPoints,
          placement = standing.placement,
          scoreDelta = standing.finalPoints - scheduledSeat.initialPoints,
          uma = standing.uma,
          oka = standing.oka
        )
      },
      paifuId = Some(paifu.id),
      finalizedBy = finalizedBy,
      notes = Vector.empty
    )
