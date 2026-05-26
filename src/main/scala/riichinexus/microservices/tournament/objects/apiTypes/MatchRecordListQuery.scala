package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.{PlayerId, TableId, TournamentId, TournamentStageId}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class MatchRecordListQuery(
    playerId: Option[PlayerId] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
