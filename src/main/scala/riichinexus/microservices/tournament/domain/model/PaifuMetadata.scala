package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class PaifuMetadata(
    recordedAt: Instant,
    source: String,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    matchRecordId: Option[MatchRecordId] = None
) derives CanEqual:
  require(source.trim.nonEmpty, "Paifu source cannot be empty")
  require(seats.size == 4, "Paifu metadata must contain four seats")
  require(seats.map(_.playerId).distinct.size == seats.size, "Paifu seats must contain unique players")
  require(seats.map(_.seat).distinct.size == seats.size, "Paifu seats must contain unique winds")

