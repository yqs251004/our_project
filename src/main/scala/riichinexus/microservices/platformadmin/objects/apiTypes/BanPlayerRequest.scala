package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class BanPlayerRequest(
    operatorId: PlayerId,
    reason: String
) derives ReadWriter
