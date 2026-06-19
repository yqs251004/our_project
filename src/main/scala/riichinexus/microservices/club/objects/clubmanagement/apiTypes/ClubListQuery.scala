package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ClubListQuery 表示俱乐部列表查询 的列表或详情查询条件。 */

final case class ClubListQuery(
    activeOnly: Option[Boolean] = None,
    joinableOnly: Option[Boolean] = None,
    memberId: Option[String] = None,
    adminId: Option[String] = None,
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubListQuery:
  given ReadWriter[ClubListQuery] = macroRW
