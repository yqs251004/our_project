package riichinexus.microservices.player.objects

/** RankSnapshot 表示前后端共享的等级快照 数据结构，包含platform、tier、stars。 */

final case class RankSnapshot(
    platform: RankPlatform,
    tier: String,
    stars: Option[Int] = None
)
