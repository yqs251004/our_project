package riichinexus.microservices.club.objects

import riichinexus.domain.model.{ClubId, ClubMembershipApplicationStatus, PlayerId, PlayerStatus}

final case class ClubListQuery(
    activeOnly: Boolean = false,
    joinableOnly: Boolean = false,
    memberId: Option[PlayerId] = None,
    adminId: Option[PlayerId] = None,
    name: Option[String] = None
)

final case class ClubTournamentParticipationQuery(
    scope: String = "recent",
    viewer: Option[PlayerId] = None
)

final case class ClubMemberQuery(
    status: Option[PlayerStatus] = None,
    nickname: Option[String] = None
)

final case class ClubPrivilegeSnapshotQuery(
    playerId: Option[PlayerId] = None,
    privilege: Option[String] = None,
    rankCode: Option[String] = None
)

final case class ClubApplicationQuery(
    operatorId: Option[PlayerId] = None,
    guestSessionId: Option[String] = None,
    status: Option[ClubMembershipApplicationStatus] = None,
    applicantUserId: Option[String] = None,
    displayName: Option[String] = None
)
