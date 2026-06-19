package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ClubApplicationDetailQuery 表示俱乐部申请详情查询 的列表或详情查询条件。 */

final case class ClubApplicationDetailQuery(
    operatorId: Option[String] = None
)

object ClubApplicationDetailQuery:
  given ReadWriter[ClubApplicationDetailQuery] = macroRW
