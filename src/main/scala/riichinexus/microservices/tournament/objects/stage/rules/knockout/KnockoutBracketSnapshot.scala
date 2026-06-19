package riichinexus.microservices.tournament.objects.stage.rules.knockout

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** KnockoutBracketSnapshot 表示前后端共享的KnockoutBracket快照 数据结构，包含赛事 ID、阶段 ID、生成时间、bracketSize、qualifiedPlayerIds、rounds等。 */

final case class KnockoutBracketSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    bracketSize: Int,
    qualifiedPlayerIds: Vector[PlayerId],
    rounds: Vector[KnockoutBracketRound],
    summary: String
) derives ReadWriter
