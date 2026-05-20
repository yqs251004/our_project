package riichinexus.microservices.publicquery.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ScheduleQuery(
    tournamentStatus: Option[TournamentStatus] = None,
    stageStatus: Option[StageStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

final case class PlayerLeaderboardQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

final case class PublicTournamentQuery(
    status: Option[TournamentStatus] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

final case class PublicClubQuery(
    name: Option[String] = None,
    relation: Option[ClubRelationKind] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

final case class PublicClubLeaderboardQuery(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

object PublicQueryQueries:
  given ReadWriter[ScheduleQuery] = macroRW
  given ReadWriter[PlayerLeaderboardQuery] = macroRW
  given ReadWriter[PublicTournamentQuery] = macroRW
  given ReadWriter[PublicClubQuery] = macroRW
  given ReadWriter[PublicClubLeaderboardQuery] = macroRW
