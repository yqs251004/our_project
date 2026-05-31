package riichinexus.microservices.tournament.objects.tablemanagement.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class StageTableQuery(
    status: Option[TableStatus] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
