package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import upickle.default.{ReadWriter, macroRW}

/** ClubApplicationListQuery 表示俱乐部申请列表查询 的列表或详情查询条件。 */

final case class ClubApplicationListQuery(
    operatorId: String,
    status: Option[ClubApplicationStatus] = None,
    playerId: Option[String] = None,
    displayName: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubApplicationListQuery:
  given ReadWriter[ClubApplicationListQuery] = macroRW
