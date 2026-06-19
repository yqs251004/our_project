package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.ReadWriter

/** ClubMembershipApplicantView 表示俱乐部成员资格申请人视图 的前端展示视图，包含玩家 ID、显示名、playerStatus、currentRank、elo、clubIds。 */

final case class ClubMembershipApplicantView(
    playerId: Option[String],
    displayName: String,
    playerStatus: Option[String],
    currentRank: Option[RankSnapshot],
    elo: Option[Int],
    clubIds: Vector[String]
) derives ReadWriter
