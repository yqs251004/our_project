package riichinexus.microservices.tournament.objects.stage.rules.progression

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given

import riichinexus.microservices.tournament.objects.stage.ranking.StageStandingEntry
import upickle.default.{ReadWriter, macroRW}

/** 按当前排名和晋级规则生成的阶段晋级预览。
  *
  * 快照保留规则、排名、晋级玩家、候补玩家和摘要说明，供后台确认阶段完成前检查晋级结果。
  */
final case class StageAdvancementSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    rule: AdvancementRule,
    standings: Vector[StageStandingEntry],
    qualifiedPlayerIds: Vector[PlayerId],
    reservePlayerIds: Vector[PlayerId] = Vector.empty,
    summary: String
)

object StageAdvancementSnapshot:
  given ReadWriter[StageAdvancementSnapshot] = macroRW
