package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

/** 描述一张桌采用的日本麻将规则配置；该类型前后端字段一致，所以不额外拆 View。 */
final case class MahjongRuleset(
    gameLength: MahjongGameLength = MahjongGameLength.Hanchan,
    initialPoints: Int = 25000,
    targetPoints: Int = 30000,
    akaDora: Boolean = true,
    akaDoraCount: Int = 3,
    openTanyao: Boolean = true,
    doubleRon: Boolean = true,
    tripleRonAbortiveDraw: Boolean = false,
    nagashiMangan: Boolean = true,
    allowMultipleYakuman: Boolean = true,
    bankruptcyEnd: Boolean = true,
    allLastDealerFinishAsTop: Boolean = false,
    minHan: Int = 1
)
