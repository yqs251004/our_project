package riichinexus.microservices.club.domain.model

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.objects.ClubPrivilegeCode

final case class ClubMemberPrivilegeSnapshot(
    playerId: PlayerId,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[ClubPrivilegeCode],
    isAdmin: Boolean,
    internalTitle: Option[String] = None
) derives CanEqual
