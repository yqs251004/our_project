package riichinexus.microservices.club.domain.relation.model

import java.time.Instant

import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.objects.relation.ClubRelationKind

import riichinexus.system.json.JsonCodecs.given

/** 当前俱乐部与另一个俱乐部之间的关系记录。
  *
  * 关系用于公开展示、联赛协作或黑名单场景，并保留更新时间与备注，方便后台解释关系来源。
  */
final case class ClubRelation(
    targetClubId: ClubId,
    relation: ClubRelationKind,
    updatedAt: Instant,
    note: Option[String] = None
)
