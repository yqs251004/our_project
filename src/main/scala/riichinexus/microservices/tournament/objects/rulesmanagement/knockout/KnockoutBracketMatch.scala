package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** KnockoutBracketMatch 表示前后端共享的KnockoutBracket对局 数据结构，包含 ID、roundNumber、position、lane、slots、sourceMatchIds等。 */

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
