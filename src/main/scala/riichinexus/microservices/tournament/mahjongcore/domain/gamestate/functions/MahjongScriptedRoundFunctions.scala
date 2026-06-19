package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.tile.functions.MahjongTileFunctions.*
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.paifumanagement.{KyokuDescriptor, PaifuTile}
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId, TableSeat}

private[tournament] object MahjongScriptedRoundFunctions:
  def initialPoints(seed: String): Map[SeatWind, Int] =
    val seedKey = seed.toLowerCase
    if seedKey.contains("thu-demo-akagi-final-score-carry") then
      Map(
        SeatWind.East -> 18000,
        SeatWind.South -> 30000,
        SeatWind.West -> 19000,
        SeatWind.North -> 33000
      )
    else Map.empty

  def tableSeatsForSeed(tableId: TableId, ruleset: MahjongRuleset, seed: String): Option[Vector[TableSeat]] =
    val seedKey = seed.toLowerCase
    Option.when(seedKey.contains("thu-demo-akagi-final") || seedKey.contains("thu-demo-reset-normal-three-rounds"))(
      Vector(
        TableSeat(SeatWind.East, PlayerId(tableId.value + "-north"), initialPoints = ruleset.initialPoints),
        TableSeat(SeatWind.South, PlayerId(tableId.value + "-south"), initialPoints = ruleset.initialPoints),
        TableSeat(SeatWind.West, PlayerId(tableId.value + "-west"), initialPoints = ruleset.initialPoints),
        TableSeat(SeatWind.North, PlayerId(tableId.value + "-east"), initialPoints = ruleset.initialPoints)
      )
    )

  def wallForRound(
      ruleset: MahjongRuleset,
      seed: String,
      descriptor: KyokuDescriptor,
      orderedSeats: Vector[(SeatWind, PlayerId)],
      showcaseMode: Boolean
  ): Option[Vector[PaifuTile]] =
    val seedKey = seed.toLowerCase
    val demoWall =
      Option.when(seedKey.contains("thu-demo-heavenly-chuuren") && descriptor.roundWind == SeatWind.East && descriptor.handNumber == 1)(
        heavenlyChuurenWall
      ).orElse(
        Option.when(seedKey.contains("thu-demo-akagi-final") && descriptor.roundWind == SeatWind.East && descriptor.handNumber == 1)(
          akagiFinalWall
        )
      ).orElse(
        Option.when(showcaseMode && descriptor.roundWind == SeatWind.East && descriptor.handNumber == 2)(
          ScriptedRoundWall(showcaseEast2InitialHands, showcaseEast2EastDraw, showcaseEast2DoraIndicator)
        )
      )

    demoWall.flatMap(scriptedWall(ruleset, orderedSeats, _))

  private final case class ScriptedRoundWall(
      initialHands: Map[SeatWind, Vector[PaifuTile]],
      eastDraw: PaifuTile,
      doraIndicator: PaifuTile,
      liveTailPrefix: Vector[PaifuTile] = Vector.empty,
      deadWallPrefix: Vector[PaifuTile] = Vector.empty
  )

  private val showcaseEast2InitialHands: Map[SeatWind, Vector[PaifuTile]] =
    Map(
      SeatWind.East -> showcaseTiles("1m", "9m", "1s", "9s", "1p", "9p", "1z", "2z", "3z", "4z", "5z", "6z", "7z"),
      SeatWind.South -> showcaseTiles("1p", "1p", "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p", "9p", "9p"),
      SeatWind.West -> showcaseTiles("1s", "1s", "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s", "9s", "9s"),
      SeatWind.North -> showcaseTiles("1m", "1m", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "9m", "9m")
    )

  private val showcaseEast2EastDraw: PaifuTile = PaifuTile("0p")
  private val showcaseEast2DoraIndicator: PaifuTile = PaifuTile("4z")

  private val heavenlyChuurenWall = ScriptedRoundWall(
    initialHands = Map(
      SeatWind.East -> showcaseTiles("1m", "1m", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "9m", "9m")
    ),
    eastDraw = PaifuTile("5m"),
    doraIndicator = PaifuTile("4z")
  )

  private val akagiFinalWall = ScriptedRoundWall(
    initialHands = Map(
      SeatWind.East -> showcaseTiles("3m", "4m", "5m", "6m", "7m", "8m", "5p", "1z", "2z", "3z", "5z", "6z", "7z"),
      SeatWind.South -> showcaseTiles("1s", "1s", "2s", "3s", "4s", "7s", "6s", "6s", "6s", "8s", "8s", "8s", "4z"),
      SeatWind.West -> showcaseTiles("1m", "1m", "2m", "4m", "4s", "6s", "7s", "9p", "9p", "2z", "3z", "4z", "4z"),
      SeatWind.North -> showcaseTiles("1m", "9m", "1p", "9p", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "7z", "7z")
    ),
    eastDraw = PaifuTile("8p"),
    doraIndicator = PaifuTile("9s"),
    liveTailPrefix = showcaseTiles(
      "1s", // South/Akagi draws the third 1s and cuts North.
      "4p", // West/Yasuoka first visible draw.
      "2p", // North/Washizu first visible draw and cut.
      "3p", // East/Suzuki second visible draw and cut.
      "9m", // South/Akagi opaque filler; 7s would be a legal tsumo in ordinary riichi.
      "8s", // West/Yasuoka source draw; he cuts 4p in the normal-rules adaptation.
      "5z", // North/Washizu draws a second white.
      "2m", // East/Suzuki draw before cutting 8m.
      "1s", // South/Akagi draws the fourth 1s and declares ankan.
      "2s", // West/Yasuoka source draw.
      "6z", // North/Washizu draws a second green.
      "1p", // East/Suzuki source draw.
      "4s", // South/Akagi redraws 4s after the rinshan tile and cuts green.
      "5m", // West/Yasuoka source draw.
      "4p", // North/Washizu source draw and cut.
      "6p", // East/Suzuki source draw and cut.
      "2s", // South/Akagi source draw and cut.
      "1z", // West/Yasuoka source draw before cutting 2s.
      "8m", // North/Washizu source draw and cut.
      "6m", // East/Suzuki source draw.
      "9p", // South/Akagi source draw and cut.
      "6m", // West/Yasuoka source draw and cut.
      "3p", // North/Washizu source draw and cut.
      "7p", // East/Suzuki final source draw before cutting white from hand.
      "5s" // South/Akagi draws the clean chinitsu winner after the missed Pon.
    ),
    deadWallPrefix = showcaseTiles("6z")
  )

  private def scriptedWall(
      ruleset: MahjongRuleset,
      orderedSeats: Vector[(SeatWind, PlayerId)],
      script: ScriptedRoundWall
  ): Option[Vector[PaifuTile]] =
    val explicitHands = script.initialHands.values.flatten.toVector
    val reservedTiles = explicitHands ++ Vector(script.eastDraw, script.doraIndicator) ++ script.liveTailPrefix ++ script.deadWallPrefix
    removeTiles(fullWall(ruleset), reservedTiles).flatMap { initialRemaining =>
      var remaining = initialRemaining
      val completedHands =
        orderedSeats.map { case (wind, _) =>
          val hand = script.initialHands.getOrElse(
            wind, {
              val filler = remaining.take(13)
              remaining = remaining.drop(13)
              filler
            }
          )
          wind -> hand
        }.toMap

      val livePrefix =
        (0 until 13).toVector.flatMap { tileIndex =>
          orderedSeats.map { case (wind, _) => completedHands(wind)(tileIndex) }
        } :+ script.eastDraw
      val liveWallSize = 136 - 14
      val liveTailSize = liveWallSize - livePrefix.size

      val liveFillSize = liveTailSize - script.liveTailPrefix.size
      val deadFillSize = 13 - script.deadWallPrefix.size
      if liveTailSize < script.liveTailPrefix.size || deadFillSize < 0 || remaining.size < liveFillSize + deadFillSize then None
      else
        val liveTail = script.liveTailPrefix ++ remaining.take(liveFillSize)
        val deadFill = remaining.drop(liveFillSize)
        val deadTiles = script.deadWallPrefix ++ deadFill
        val deadSource =
          deadTiles.take(4) ++
            Vector(script.doraIndicator) ++
            deadTiles.drop(4).take(9)
        Some(livePrefix ++ liveTail ++ deadSource)
    }

  private def showcaseTiles(values: String*): Vector[PaifuTile] =
    values.toVector.map(PaifuTile(_))
