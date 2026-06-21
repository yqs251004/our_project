package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.tournament.objects.stage.table.SeatWind

/** 牌谱中定位一小局的场风、局序和本场数。
  *
  * 该描述用于回放标题、结算说明和牌谱导入，明确当前是东/南等场次中的第几局与第几本场。
  */
final case class KyokuDescriptor(
    roundWind: SeatWind,
    handNumber: Int,
    honba: Int = 0
)
