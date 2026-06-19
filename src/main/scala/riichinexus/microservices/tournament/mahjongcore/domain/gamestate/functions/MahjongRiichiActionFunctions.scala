package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongSeatState
import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.functions.MahjongHandAnalysisFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions
import riichinexus.microservices.tournament.objects.paifu.PaifuTile

/** MahjongRiichiActionFunctions 提供麻将立直动作相关的领域计算、校验和转换函数。 */

private[mahjongcore] object MahjongRiichiActionFunctions:
  private[mahjongcore] def canDeclareRiichi(seat: MahjongSeatState): Boolean =
    !seat.riichi && seat.points >= 1000 && seat.melds.forall(_.closed)

  private[mahjongcore] def leavesTenpaiAfterDiscard(seat: MahjongSeatState, discardTile: PaifuTile): Boolean =
    val source = seat.handTiles ++ seat.drawTile.toVector
    MahjongTileFunctions.removeTiles(source, Vector(discardTile)).exists { remaining =>
      MahjongHandAnalysisFunctions.calculateShanten(remaining, seat.melds.size, allowSpecialHands = seat.melds.isEmpty) == 0
    }
