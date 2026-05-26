package riichinexus.microservices.player.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.PlayerStatus

final case class PlayerListQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)
