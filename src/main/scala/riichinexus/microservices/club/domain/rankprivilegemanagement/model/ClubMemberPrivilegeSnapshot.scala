package riichinexus.microservices.club.domain.rankprivilegemanagement.model

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode

final case class ClubMemberPrivilegeSnapshot(
    playerId: PlayerId,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[ClubPrivilegeCode],
    isAdmin: Boolean,
    internalTitle: Option[String] = None
) derives CanEqual
