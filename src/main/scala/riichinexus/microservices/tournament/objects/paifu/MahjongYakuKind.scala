package riichinexus.microservices.tournament.objects.paifu

/** MahjongYakuKind 枚举麻将役种类型 可使用的公开取值。 */

enum MahjongYakuKind:
  case KokushiMusouThirteenWait // 国士无双十三面
  case KokushiMusou // 国士无双
  case PureChuurenPoutou // 纯正九莲宝灯
  case ChuurenPoutou // 九莲宝灯
  case Tsuuiisou // 字一色
  case Ryuuiisou // 绿一色
  case Chinroutou // 清老头
  case SuuankouTanki // 四暗刻单骑
  case Suuankou // 四暗刻
  case Daisangen // 大三元
  case Daisuushi // 大四喜
  case Shousuushi // 小四喜
  case Suukantsu // 四杠子
  case Tenhou // 天和
  case Chiihou // 地和
  case Chiitoitsu // 七对子
  case MenzenTsumo // 门前清自摸和
  case DoubleRiichi // 双立直
  case Riichi // 立直
  case Ippatsu // 一发
  case RinshanKaihou // 岭上开花
  case HaiteiRaoyue // 海底捞月
  case HouteiRaoyui // 河底捞鱼
  case NagashiMangan // 流局满贯
  case Tanyao // 断幺九
  case YakuhaiHaku // 役牌:白
  case YakuhaiHatsu // 役牌:发
  case YakuhaiChun // 役牌:中
  case RoundWind // 场风牌
  case SeatWind // 自风牌
  case Pinfu // 平和
  case Ryanpeikou // 二杯口
  case Iipeikou // 一杯口
  case Toitoi // 对对和
  case Sanankou // 三暗刻
  case Sankantsu // 三杠子
  case Shousangen // 小三元
  case SanshokuDoujun // 三色同顺
  case Ittsu // 一气通贯
  case Chinitsu // 清一色
  case Honitsu // 混一色
  case Honroutou // 混老头
  case Junchan // 纯全带幺九
  case Chanta // 混全带幺九
  case SanshokuDoukou // 三色同刻
  case Dora // 宝牌
  case AkaDora // 红宝牌
  case UraDora // 里宝牌

  def yaku(han: Int): Yaku =
    Yaku(this, han)

object MahjongYakuKind:
  def toString(kind: MahjongYakuKind): String =
    kind.productPrefix

  def fromString(value: String): MahjongYakuKind =
    MahjongYakuKind.valueOf(value)
