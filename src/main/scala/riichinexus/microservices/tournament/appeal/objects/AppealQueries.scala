package riichinexus.microservices.tournament.appeal.objects

import java.time.Instant

import riichinexus.domain.model.*

final case class AppealListQuery(
    status: Option[AppealStatus] = None,
    priority: Option[AppealPriority] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None,
    openedBy: Option[PlayerId] = None,
    assigneeId: Option[PlayerId] = None,
    overdueOnly: Boolean = false,
    dueBefore: Option[Instant] = None,
    dueAfter: Option[Instant] = None,
    asOf: Instant = Instant.now()
)
