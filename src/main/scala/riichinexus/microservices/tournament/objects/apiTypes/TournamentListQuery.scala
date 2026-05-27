package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.tournament.domain.model.TournamentStatus
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentListQuery(
    status: Option[TournamentStatus] = None,
    adminId: Option[PlayerId] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
