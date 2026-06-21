package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model

/** 标准手牌拆解中一个面子的结构类型。
  *
  * 顺子、刻子和杠子会被役种分析分别处理，用于判断平和、一杯口、对对和、三杠子等形状役。
  */
enum MahjongHandMeldType:
  case Shuntsu
  case Koutsu
  case Kantsu
