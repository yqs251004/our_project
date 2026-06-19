package riichinexus.microservices.club.domain.relationmanagement.model

import java.time.Instant

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind

import riichinexus.system.json.JsonCodecs.given
/** ClubRelation 表示后端领域中的俱乐部关系状态或规则，包含targetClubId、relation、更新时间、note。 */
final case class ClubRelation(
    targetClubId: ClubId,
    relation: ClubRelationKind,
    updatedAt: Instant,
    note: Option[String] = None
)