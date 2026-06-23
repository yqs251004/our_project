package riichinexus.microservices.club.objects.relation

import riichinexus.system.json.ClubJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 前端展示的一条俱乐部对外关系。
  *
  * 目标俱乐部以字符串 ID 暴露给页面，关系类型用于详情页、目录行和关系筛选展示。
  */
final case class ClubRelationView(
    targetClubId: String,
    relation: ClubRelationKind
)

object ClubRelationView:
  given ReadWriter[ClubRelationView] = macroRW
