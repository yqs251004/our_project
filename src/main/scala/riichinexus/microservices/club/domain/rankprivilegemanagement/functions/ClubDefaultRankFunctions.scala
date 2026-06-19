package riichinexus.microservices.club.domain.rankprivilegemanagement.functions

import riichinexus.microservices.club.domain.rankprivilegemanagement.model.ClubDefaultRank
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubRankNode

private[club] object ClubDefaultRankFunctions:
  val all: Vector[ClubDefaultRank] =
    Vector(
      ClubDefaultRank.Rookie,
      ClubDefaultRank.Member,
      ClubDefaultRank.Core,
      ClubDefaultRank.Ace
    )

  val defaultRankTree: Vector[ClubRankNode] =
    all.map(rank =>
      ClubRankNode(rank.code, rank.label, rank.minimumContribution)
    )
