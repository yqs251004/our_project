package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifumanagement.Yaku

/** YakuFunctions 提供役种相关的领域计算、校验和转换函数。 */

private[tournament] object YakuFunctions:
  def validate(yaku: Yaku): Unit =
    require(yaku.han > 0, "Yaku han must be positive")
