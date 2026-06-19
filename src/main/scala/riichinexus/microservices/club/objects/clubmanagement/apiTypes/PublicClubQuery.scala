package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import upickle.default.{ReadWriter, macroRW}

/** PublicClubQuery 表示公开俱乐部查询 的列表或详情查询条件。 */

final case class PublicClubQuery(
    name: Option[String] = None,
    relation: Option[ClubRelationKind] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PublicClubQuery:
  given ReadWriter[PublicClubQuery] = macroRW
