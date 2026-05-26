package riichinexus.microservices.publicquery.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{MatchRecordId, TableId, TournamentId, TournamentStageId}
import upickle.default.*

final case class PublicClubRecentMatchView(
    matchRecordId: String,
    tournamentId: String,
    tournamentName: String,
    stageId: String,
    stageName: String,
    tableId: String,
    generatedAt: String,
    seats: Vector[PublicClubRecentMatchSeatView]
) derives CanEqual

object PublicClubRecentMatchView:
  given ReadWriter[PublicClubRecentMatchView] = macroRW

  def apply(
      matchRecordId: MatchRecordId,
      tournamentId: TournamentId,
      tournamentName: String,
      stageId: TournamentStageId,
      stageName: String,
      tableId: TableId,
      generatedAt: Instant,
      seats: Vector[PublicClubRecentMatchSeatView]
  ): PublicClubRecentMatchView =
    PublicClubRecentMatchView(
      matchRecordId = matchRecordId.value,
      tournamentId = tournamentId.value,
      tournamentName = tournamentName,
      stageId = stageId.value,
      stageName = stageName,
      tableId = tableId.value,
      generatedAt = generatedAt.toString,
      seats = seats
    )
