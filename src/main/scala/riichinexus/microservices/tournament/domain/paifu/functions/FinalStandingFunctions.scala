package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifu.FinalStanding

/** FinalStandingFunctions 提供最终排名相关的领域计算、校验和转换函数。 */

private[tournament] object FinalStandingFunctions:
  def validate(standing: FinalStanding): Unit =
    require(standing.placement >= 1 && standing.placement <= 4, "Placement must be between 1 and 4")
