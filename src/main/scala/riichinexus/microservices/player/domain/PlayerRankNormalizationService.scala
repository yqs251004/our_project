package riichinexus.microservices.player.domain

import riichinexus.microservices.player.objects.RankSnapshot

object PlayerRankNormalizationService:
  final case class NormalizedRank(
      score: Int,
      source: String
  )

  def normalize(rank: RankSnapshot): Option[NormalizedRank] =
    None
