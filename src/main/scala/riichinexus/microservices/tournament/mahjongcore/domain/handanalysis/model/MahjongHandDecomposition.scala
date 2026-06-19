package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model

/** MahjongHandDecomposition 枚举领域内麻将手牌拆解 的可选状态或类型。 */

enum MahjongHandMeldType:
  case Shuntsu
  case Koutsu
  case Kantsu

final case class MahjongHandMeld(
    meldType: MahjongHandMeldType,
    tileIndex: Int,
    concealed: Boolean
)

final case class MahjongHandDecomposition(
    melds: Vector[MahjongHandMeld],
    pairIndex: Int
)
