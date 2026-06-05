package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import upickle.default.*

enum MahjongGameLength:
  case OneKyoku
  case Tonpu
  case Hanchan

object MahjongGameLength:
  given ReadWriter[MahjongGameLength] =
    readwriter[String].bimap(_.toString, MahjongGameLength.valueOf)

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
    minHan: Int = 1
):
  def normalizedAkaDoraCount: Int =
    if !akaDora then 0 else math.max(0, math.min(akaDoraCount, 3))

  def normalizedMinHan: Int =
    math.max(1, minHan)

object MahjongRuleset:
  given ReadWriter[MahjongRuleset] =
    readwriter[ujson.Value].bimap[MahjongRuleset](
      ruleset =>
        ujson.Obj(
          "gameLength" -> writeJs(ruleset.gameLength),
          "initialPoints" -> ruleset.initialPoints,
          "targetPoints" -> ruleset.targetPoints,
          "akaDora" -> ruleset.akaDora,
          "akaDoraCount" -> ruleset.akaDoraCount,
          "openTanyao" -> ruleset.openTanyao,
          "doubleRon" -> ruleset.doubleRon,
          "tripleRonAbortiveDraw" -> ruleset.tripleRonAbortiveDraw,
          "nagashiMangan" -> ruleset.nagashiMangan,
          "allowMultipleYakuman" -> ruleset.allowMultipleYakuman,
          "bankruptcyEnd" -> ruleset.bankruptcyEnd,
          "minHan" -> ruleset.minHan
        ),
      {
        case obj: ujson.Obj =>
          val akaDora = obj.value.get("akaDora").fold(true)(read[Boolean](_))
          MahjongRuleset(
            gameLength = obj.value.get("gameLength").fold(MahjongGameLength.Hanchan)(read[MahjongGameLength](_)),
            initialPoints = obj.value.get("initialPoints").fold(25000)(read[Int](_)),
            targetPoints = obj.value.get("targetPoints").fold(30000)(read[Int](_)),
            akaDora = akaDora,
            akaDoraCount = obj.value.get("akaDoraCount").fold(if akaDora then 3 else 0)(read[Int](_)),
            openTanyao = obj.value.get("openTanyao").fold(true)(read[Boolean](_)),
            doubleRon = obj.value.get("doubleRon").fold(true)(read[Boolean](_)),
            tripleRonAbortiveDraw = obj.value.get("tripleRonAbortiveDraw").fold(false)(read[Boolean](_)),
            nagashiMangan = obj.value.get("nagashiMangan").fold(true)(read[Boolean](_)),
            allowMultipleYakuman = obj.value.get("allowMultipleYakuman").fold(true)(read[Boolean](_)),
            bankruptcyEnd = obj.value.get("bankruptcyEnd").fold(true)(read[Boolean](_)),
            minHan = obj.value.get("minHan").fold(1)(read[Int](_))
          )
        case json =>
          throw upickle.core.Abort(s"Expected MahjongRuleset object, got $json")
      }
    )
