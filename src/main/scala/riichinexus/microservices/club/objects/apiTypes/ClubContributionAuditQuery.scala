package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ClubContributionAuditQuery(
    operatorId: PlayerId,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter

