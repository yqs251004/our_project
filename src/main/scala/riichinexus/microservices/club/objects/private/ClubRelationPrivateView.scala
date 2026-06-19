package riichinexus.microservices.club.objects.`private`

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind

/** ClubRelationPrivateView 表示后端内部使用的俱乐部关系后端内部视图 read model，包含targetClubId、relation。 */

final case class ClubRelationPrivateView(
    targetClubId: ClubId,
    relation: ClubRelationKind
)
