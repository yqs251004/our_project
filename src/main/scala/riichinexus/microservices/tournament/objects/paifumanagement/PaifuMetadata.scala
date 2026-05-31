package riichinexus.microservices.tournament.objects.paifumanagement

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.tablemanagement.TableSeat

final case class PaifuMetadata(
    recordedAt: Instant,
    source: String,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    matchRecordId: Option[MatchRecordId] = None
) derives CanEqual
