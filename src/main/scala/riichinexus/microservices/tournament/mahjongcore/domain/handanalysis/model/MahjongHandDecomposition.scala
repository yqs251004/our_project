package riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model

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
