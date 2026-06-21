package riichinexus.microservices.club.domain.membershipmanagement.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given

/** 俱乐部为成员设置的展示称号。
  *
  * 称号记录被授予玩家、称号文本、授予人、授予时间和备注，用于俱乐部成员列表与个人资料展示。
  */
final case class ClubTitleAssignment(
    playerId: PlayerId,
    title: String,
    assignedBy: PlayerId,
    assignedAt: Instant,
    note: Option[String] = None
)
