package riichinexus.microservices.club.domain.model

import riichinexus.domain.model.PlayerId

final case class ClubMemberPrivilegeSnapshot(
    playerId: PlayerId,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[String],
    isAdmin: Boolean,
    internalTitle: Option[String] = None
) derives CanEqual
