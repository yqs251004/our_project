package riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot as DomainRankSnapshot}
import upickle.default.*

final case class RankSnapshotView(
    platform: RankPlatform,
    tier: String,
    stars: Option[Int]
) derives ReadWriter

object RankSnapshotView:
  def fromDomain(rank: DomainRankSnapshot): RankSnapshotView =
    RankSnapshotView(
      platform = rank.platform,
      tier = rank.tier,
      stars = rank.stars
    )
