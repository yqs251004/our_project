package riichinexus.microservices.tournament.objects.stage.rules.knockout

import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 淘汰赛 bracket 中的一场对局节点。
  *
  * 节点记录轮次、位置、线路、席位来源、晋级人数、下一场关联、绑定牌桌和结果状态，是 bracket 推进的核心单元。
  */
final case class KnockoutBracketMatch(
    id: String,
    roundNumber: Int,
    position: Int,
    lane: KnockoutLane = KnockoutLane.Championship,
    slots: Vector[KnockoutBracketSlot],
    sourceMatchIds: Vector[String] = Vector.empty,
    advancementCount: Int,
    nextMatchId: Option[String] = None,
    tableId: Option[TableId] = None,
    unlocked: Boolean = false,
    completed: Boolean = false,
    results: Vector[KnockoutBracketResult] = Vector.empty
) derives ReadWriter
