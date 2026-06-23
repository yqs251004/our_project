package riichinexus.microservices.club.domain.membership.model

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId

import riichinexus.system.json.JsonCodecs.given

/** 俱乐部成员当前贡献值的领域记录。
  *
  * 贡献值会影响等级树匹配和权限快照，记录更新人、更新时间和备注可以追溯人工调整或赛事结算带来的变动。
  */
final case class ClubMemberContribution(
    playerId: PlayerId,
    amount: Int,
    updatedAt: Instant,
    updatedBy: PlayerId,
    note: Option[String] = None
)
