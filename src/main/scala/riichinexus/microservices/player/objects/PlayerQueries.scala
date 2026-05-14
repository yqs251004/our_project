package riichinexus.microservices.player.objects

import riichinexus.domain.model.*

final case class PlayerListQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    nickname: Option[String] = None
)
