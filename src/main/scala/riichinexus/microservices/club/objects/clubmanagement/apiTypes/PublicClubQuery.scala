package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import upickle.default.{ReadWriter, macroRW}

/** 公共大厅查询俱乐部目录的过滤和分页参数。
  *
  * 访客可以按名称和关系类型筛选公开俱乐部，结果只返回适合大厅展示的目录摘要。
  */
final case class PublicClubQuery(
    name: Option[String] = None,
    relation: Option[ClubRelationKind] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PublicClubQuery:
  given ReadWriter[PublicClubQuery] = macroRW
