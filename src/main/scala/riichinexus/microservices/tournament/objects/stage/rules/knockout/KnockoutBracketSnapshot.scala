package riichinexus.microservices.tournament.objects.stage.rules.knockout

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 某个淘汰赛阶段当前签表的完整快照。
  *
  * 快照记录参赛规模、入围玩家、各轮对局和摘要说明，供公开 bracket 展示、后台排桌和晋级推进复用。
  */
final case class KnockoutBracketSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    bracketSize: Int,
    qualifiedPlayerIds: Vector[PlayerId],
    rounds: Vector[KnockoutBracketRound],
    summary: String
)

object KnockoutBracketSnapshot:
  given ReadWriter[KnockoutBracketSnapshot] = macroRW
