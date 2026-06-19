package riichinexus.microservices.tournament.objects.stage.ranking

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** StageStandingEntry 表示前后端共享的阶段排名条目 数据结构，包含玩家 ID、matchesPlayed、placementPoints、totalScoreDelta、totalFinalPoints、averagePlacement等。 */

final case class StageStandingEntry(
    playerId: PlayerId,
    matchesPlayed: Int,
    placementPoints: Int,
    totalScoreDelta: Int,
    totalFinalPoints: Int,
    averagePlacement: Double,
    qualified: Boolean = false,
    seed: Option[Int] = None
) derives ReadWriter
