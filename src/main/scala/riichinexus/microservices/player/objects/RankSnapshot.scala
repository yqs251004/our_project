package riichinexus.microservices.player.objects

final case class RankSnapshot(
    platform: RankPlatform,
    tier: String,
    stars: Option[Int] = None
) derives CanEqual
