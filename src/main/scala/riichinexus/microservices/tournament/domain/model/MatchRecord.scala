package riichinexus.microservices.tournament.domain.model

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class MatchRecord(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    stageRoundNumber: Int,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResult],
    paifuId: Option[PaifuId] = None,
    finalizedBy: Option[PlayerId] = None,
    sourceEvent: String = "table-state-machine",
    notes: Vector[String] = Vector.empty
) derives CanEqual:
  require(seatResults.size == 4, "Match record must contain four seat results")
  require(seatResults.map(_.playerId).distinct.size == 4, "Match record players must be unique")
  require(seatResults.map(_.seat).distinct.size == 4, "Match record seats must be unique")
  require(seatResults.map(_.placement).distinct.size == 4, "Match record placements must be unique")
  require(stageRoundNumber >= 1, "Match record stage round number must be positive")

  def playerIds: Vector[PlayerId] =
    seatResults.map(_.playerId)

object MatchRecord:
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
      id = IdGenerator.matchRecordId(),
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
