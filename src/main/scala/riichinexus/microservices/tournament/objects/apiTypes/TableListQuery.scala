package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.{PlayerId, TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.TableStatus
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TableListQuery(
    status: Option[TableStatus] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
