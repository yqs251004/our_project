package riichinexus.microservices.publicquery.objects

import riichinexus.domain.model.{ClubId, ClubRelationKind, PlayerStatus, StageStatus, TournamentStatus}

final case class PublicScheduleQuery(
    tournamentStatus: Option[TournamentStatus] = None,
    stageStatus: Option[StageStatus] = None
)

final case class PublicTournamentQuery(
    status: Option[TournamentStatus] = None,
    organizer: Option[String] = None
)

final case class PublicClubDirectoryQuery(
    name: Option[String] = None,
    relation: Option[ClubRelationKind] = None
)

final case class PublicPlayerLeaderboardQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None
)

final case class PublicClubLeaderboardQuery(
    name: Option[String] = None
)
