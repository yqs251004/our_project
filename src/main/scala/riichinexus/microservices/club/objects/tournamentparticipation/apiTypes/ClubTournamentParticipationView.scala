package riichinexus.microservices.club.objects.tournamentparticipation.apiTypes

import upickle.default.ReadWriter
import riichinexus.system.json.JsonCodecs.given

/** ClubTournamentParticipationView 表示俱乐部赛事Participation视图 的前端展示视图，包含俱乐部 ID、赛事 ID、名称、状态、clubParticipationStatus、stageName等。 */

final case class ClubTournamentParticipationView(
    clubId: String,
    tournamentId: String,
    name: String,
    status: String,
    clubParticipationStatus: String,
    stageName: Option[String],
    startsAt: String,
    endsAt: String,
    canViewDetail: Boolean,
    canSubmitLineup: Boolean,
    canDecline: Boolean
) derives ReadWriter
