package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** ClubContributionAuditQuery 表示俱乐部贡献审计查询 的列表或详情查询条件，包含operatorId、数量限制、分页偏移。 */

final case class ClubContributionAuditQuery(
    operatorId: PlayerId,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter

