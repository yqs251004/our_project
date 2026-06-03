package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.functions

import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.objects.paifumanagement.MahjongYakuKind
import riichinexus.microservices.tournament.objects.paifumanagement.Yaku

import MahjongYakuCheckSupport.*

private[functions] object MahjongDoraCheckFunctions:

  val plan: Vector[YakuCheck] =
    Vector(checkDora, checkAkaDora, checkUraDora)

  private def checkDora(state: MahjongYakuCheckState): Vector[Yaku] =
    val omote = countDora(state.allTiles, state.context.doraIndicators)
    yakuIf(omote > 0, MahjongYakuKind.Dora, omote)

  private def checkAkaDora(state: MahjongYakuCheckState): Vector[Yaku] =
    val red = redDoraCount(state.allTiles)
    yakuIf(red > 0, MahjongYakuKind.AkaDora, red)

  private def checkUraDora(state: MahjongYakuCheckState): Vector[Yaku] =
    val ura =
      if state.context.riichi || state.context.doubleRiichi then countDora(state.allTiles, state.context.uraDoraIndicators)
      else 0
    yakuIf(ura > 0, MahjongYakuKind.UraDora, ura)

