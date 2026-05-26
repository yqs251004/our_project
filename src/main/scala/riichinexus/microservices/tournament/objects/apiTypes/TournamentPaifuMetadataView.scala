package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.PaifuMetadata
import riichinexus.microservices.tournament.objects.TableSeat

final case class TournamentPaifuMetadataView(
    recordedAt: String,
    source: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    seats: Vector[TableSeat],
    matchRecordId: Option[String]
) derives CanEqual

object TournamentPaifuMetadataView:
  def fromDomain(metadata: PaifuMetadata): TournamentPaifuMetadataView =
    TournamentPaifuMetadataView(
      recordedAt = metadata.recordedAt.toString,
      source = metadata.source,
      tableId = metadata.tableId.value,
      tournamentId = metadata.tournamentId.value,
      stageId = metadata.stageId.value,
      seats = metadata.seats.map(TableSeat.fromDomain),
      matchRecordId = metadata.matchRecordId.map(_.value)
    )
