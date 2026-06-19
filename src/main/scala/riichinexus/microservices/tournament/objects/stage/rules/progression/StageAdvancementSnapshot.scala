package riichinexus.microservices.tournament.objects.stage.rules.progression

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given

import riichinexus.microservices.tournament.objects.stage.ranking.StageStandingEntry
import upickle.default.ReadWriter

/** StageAdvancementSnapshot 表示前后端共享的阶段Advancement快照 数据结构，包含赛事 ID、阶段 ID、生成时间、rule、standings、qualifiedPlayerIds等。 */

final case class StageAdvancementSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    rule: AdvancementRule,
    standings: Vector[StageStandingEntry],
    qualifiedPlayerIds: Vector[PlayerId],
    reservePlayerIds: Vector[PlayerId] = Vector.empty,
    summary: String
) derives ReadWriter
