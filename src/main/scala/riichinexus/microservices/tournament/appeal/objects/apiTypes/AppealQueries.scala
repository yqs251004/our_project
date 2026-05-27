package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

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
    asOf: Option[Instant] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
