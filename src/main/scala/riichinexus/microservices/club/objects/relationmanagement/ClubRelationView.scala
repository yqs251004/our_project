package riichinexus.microservices.club.objects.relationmanagement

import riichinexus.system.json.ClubJsonCodecs.given
import upickle.default.ReadWriter

/** ClubRelationView 表示俱乐部关系视图 的前端展示视图，包含targetClubId、relation。 */

final case class ClubRelationView(
    targetClubId: String,
    relation: ClubRelationKind
) derives ReadWriter
