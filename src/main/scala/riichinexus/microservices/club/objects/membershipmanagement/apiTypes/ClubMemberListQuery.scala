package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ClubMemberListQuery 表示俱乐部成员列表查询 的列表或详情查询条件。 */

final case class ClubMemberListQuery(
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubMemberListQuery:
  given ReadWriter[ClubMemberListQuery] = macroRW
