package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.ReadWriter

/** 入会申请中展示给审核人的申请人摘要。
  *
  * 该视图汇总可公开判断申请人的资料，例如昵称、段位、Elo、当前俱乐部归属；游客或未绑定玩家时 `playerId` 可以为空。
  */
final case class ClubMembershipApplicantView(
    playerId: Option[String],
    displayName: String,
    playerStatus: Option[String],
    currentRank: Option[RankSnapshot],
    elo: Option[Int],
    clubIds: Vector[String]
) derives ReadWriter
