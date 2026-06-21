package riichinexus.microservices.tournament.objects.paifu

/** 小局结算时附加给前端展示的结果标签。
  *
  * 标签覆盖流局类型、双/三家和牌和满贯到多倍役满等等级，供结果面板解释分数变化来源。
  */
enum RoundSettlementNote:
  case ExhaustiveDraw
  case AbortiveDrawRequested
  case TripleRonAbortiveDraw
  case DoubleRon
  case TripleRon
  case NagashiMangan
  case Mangan
  case Haneman
  case Baiman
  case Sanbaiman
  case Yakuman
  case DoubleYakuman
  case TripleYakuman
  case QuadrupleYakuman
  case QuintupleYakuman
  case SextupleYakuman
  case SeptupleYakuman
  case OctupleYakuman
  case NonupleYakuman
