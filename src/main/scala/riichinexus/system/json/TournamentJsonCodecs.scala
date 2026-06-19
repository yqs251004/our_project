package riichinexus.system.json

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachment, AppealAttachmentMediaKind, AppealAttachmentStorageKind, AppealDecisionType, AppealPriority, AppealStatus, AppealTableResolution, AppealTicket}
import riichinexus.microservices.tournament.appeal.objects.AppealDecisionLog
import riichinexus.microservices.tournament.domain.stage.model.{StageLineupSeat, StageLineupSubmission, StageTablePlan, Table, TournamentStage}
import riichinexus.microservices.tournament.domain.matchrecord.model.{MatchRecord, MatchRecordSeatResult}
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.objects.paifu.{AgariResult, AgariWinResult, FinalStanding, HandOutcome, KyokuDescriptor, MahjongYakuKind, Paifu, PaifuAction, PaifuActionType, PaifuHand, PaifuMetadata, PaifuPlayerTrack, PaifuRound, PaifuRoundPlayer, PaifuTile, PaifuTimeline, RoundSettlement, RoundSettlementNote, ScoreChange, Yaku}
import riichinexus.microservices.tournament.objects.`private`.matchrecord.{MatchRecordPrivateView, MatchRecordSeatResultPrivateView}
import riichinexus.microservices.tournament.objects.`private`.stage.{StageLineupSeatPrivateView, StageLineupSubmissionPrivateView, TournamentStagePrivateView}
import riichinexus.microservices.tournament.objects.`private`.competition.TournamentPrivateView
import riichinexus.microservices.tournament.objects.stage.rules.knockout.{KnockoutLane, KnockoutRuleConfig}
import riichinexus.microservices.tournament.objects.stage.rules.progression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementAdjustment, TournamentSettlementEntry, TournamentSettlementStatus}
import riichinexus.microservices.tournament.objects.stage.table.{SeatWind, TableSeat, TableStatus}
import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.{TournamentFormat, TournamentParticipantKind, TournamentStatus, TournamentWhitelistEntry}
import riichinexus.system.json.JsonCodecSupport.{eitherStringEnumReadWriter, stringEnumReadWriter}
import riichinexus.system.json.SharedJsonCodecs.given
import scala.util.Try
import upickle.default.{ReadWriter, macroRW, read, readwriter, writeJs}

object TournamentJsonCodecs:
  given ReadWriter[TournamentStatus] =
    stringEnumReadWriter(TournamentStatus.valueOf, _.toString)
  given ReadWriter[TournamentFormat] =
    eitherStringEnumReadWriter(
      TournamentFormat.fromString,
      TournamentFormat.toString
    )
  given ReadWriter[StageStatus] =
    stringEnumReadWriter(StageStatus.valueOf, _.toString)
  given ReadWriter[AdvancementRuleType] =
    stringEnumReadWriter(AdvancementRuleType.valueOf, _.toString)
  given ReadWriter[AdvancementRule] = macroRW
  given ReadWriter[SwissRuleConfig] = macroRW
  given ReadWriter[KnockoutRuleConfig] = macroRW
  given ReadWriter[KnockoutLane] =
    stringEnumReadWriter(KnockoutLane.valueOf, _.toString)
  given ReadWriter[SeatWind] =
    eitherStringEnumReadWriter(
      SeatWind.fromString,
      SeatWind.toString
    )
  given ReadWriter[StageLineupSeat] = macroRW
  given ReadWriter[StageLineupSubmission] = macroRW
  given ReadWriter[StageTablePlan] = macroRW
  given ReadWriter[TournamentParticipantKind] =
    stringEnumReadWriter(TournamentParticipantKind.valueOf, _.toString)
  given ReadWriter[TournamentWhitelistEntry] = macroRW
  given ReadWriter[TournamentStage] = macroRW
  given ReadWriter[TournamentSettlementStatus] =
    stringEnumReadWriter(TournamentSettlementStatus.valueOf, _.toString)
  given ReadWriter[TournamentSettlementAdjustment] = macroRW
  given ReadWriter[TournamentSettlementEntry] = macroRW
  given ReadWriter[TournamentSettlementSnapshot] = macroRW
  given ReadWriter[Tournament] = macroRW
  given ReadWriter[TableSeat] =
    readwriter[ujson.Value].bimap[TableSeat](
      seat =>
        ujson.Obj(
          "seat" -> writeJs(seat.seat),
          "playerId" -> writeJs(seat.playerId),
          "initialPoints" -> seat.initialPoints,
          "disconnected" -> seat.disconnected,
          "ready" -> seat.ready,
          "clubId" -> writeJs(seat.clubId)
        ),
      {
        case obj: ujson.Obj =>
          TableSeat(
            seat = read[SeatWind](obj("seat")),
            playerId = read[PlayerId](obj("playerId")),
            initialPoints = obj.value.get("initialPoints").fold(25000)(read[Int](_)),
            disconnected = obj.value.get("disconnected").fold(false)(read[Boolean](_)),
            ready = obj.value.get("ready").fold(false)(read[Boolean](_)),
            clubId = obj.value.get("clubId").fold(Option.empty[ClubId])(read[Option[ClubId]](_))
          )
        case json =>
          throw upickle.core.Abort(s"Expected TableSeat object, got $json")
      }
    )
  given ReadWriter[TableStatus] =
    stringEnumReadWriter(TableStatus.valueOf, _.toString)
  given ReadWriter[AppealTableResolution] =
    stringEnumReadWriter(AppealTableResolution.valueOf, _.toString)
  given ReadWriter[Table] = macroRW
  given ReadWriter[MatchRecordSeatResult] = macroRW
  given ReadWriter[MatchRecord] = macroRW
  given ReadWriter[StageLineupSeatPrivateView] = macroRW
  given ReadWriter[StageLineupSubmissionPrivateView] = macroRW
  given ReadWriter[TournamentStagePrivateView] = macroRW
  given ReadWriter[TournamentPrivateView] = macroRW
  given ReadWriter[MatchRecordSeatResultPrivateView] = macroRW
  given ReadWriter[MatchRecordPrivateView] = macroRW
  given ReadWriter[AppealAttachmentStorageKind] =
    stringEnumReadWriter(AppealAttachmentStorageKind.valueOf, _.toString)
  given ReadWriter[AppealAttachmentMediaKind] =
    stringEnumReadWriter(AppealAttachmentMediaKind.valueOf, _.toString)
  given ReadWriter[AppealAttachment] = macroRW
  given ReadWriter[AppealDecisionLog] = macroRW
  given ReadWriter[AppealPriority] =
    stringEnumReadWriter(AppealPriority.valueOf, _.toString)
  given ReadWriter[AppealStatus] =
    stringEnumReadWriter(AppealStatus.valueOf, _.toString)
  given ReadWriter[AppealDecisionType] =
    stringEnumReadWriter(AppealDecisionType.valueOf, _.toString)
  given ReadWriter[AppealTicket] = macroRW

  given ReadWriter[HandOutcome] =
    stringEnumReadWriter(HandOutcome.valueOf, _.toString)
  given ReadWriter[MahjongYakuKind] =
    stringEnumReadWriter(MahjongYakuKind.valueOf, _.productPrefix)
  given ReadWriter[Yaku] =
    readwriter[ujson.Value].bimap[Yaku](
      yaku =>
        ujson.Obj(
          "kind" -> writeJs(yaku.kind),
          "han" -> writeJs(yaku.han)
        ),
      {
        case obj: ujson.Obj =>
          Yaku(
            kind = readYakuKind(obj),
            han = read[Int](obj("han"))
          )
        case json =>
          throw upickle.core.Abort(s"Expected Yaku object, got $json")
      }
    )
  given ReadWriter[ScoreChange] = macroRW
  given ReadWriter[RoundSettlementNote] =
    readwriter[String].bimap[RoundSettlementNote](
      _.toString,
      readRoundSettlementNote
    )
  given ReadWriter[RoundSettlement] =
    readwriter[ujson.Value].bimap[RoundSettlement](
      settlement =>
        ujson.Obj(
          "riichiSticksDelta" -> settlement.riichiSticksDelta,
          "honbaPayment" -> settlement.honbaPayment,
          "notes" -> writeJs(settlement.notes)
        ),
      {
        case obj: ujson.Obj =>
          RoundSettlement(
            riichiSticksDelta = obj.value.get("riichiSticksDelta").fold(0)(read[Int](_)),
            honbaPayment = obj.value.get("honbaPayment").fold(0)(read[Int](_)),
            notes = obj.value.get("notes").fold(Vector.empty[RoundSettlementNote])(read[Vector[RoundSettlementNote]](_))
          )
        case json =>
          throw upickle.core.Abort(s"Expected RoundSettlement object, got $json")
      }
    )
  given ReadWriter[AgariWinResult] = macroRW
  given ReadWriter[AgariResult] = macroRW
  given ReadWriter[PaifuActionType] =
    stringEnumReadWriter(PaifuActionType.valueOf, _.toString)
  given ReadWriter[PaifuAction] =
    readwriter[ujson.Value].bimap[PaifuAction](
      action =>
        ujson.Obj(
          "sequenceNo" -> action.sequenceNo,
          "actor" -> writeJs(action.actor),
          "actionType" -> writeJs(action.actionType),
          "tile" -> writeJs(action.tile),
          "fromPlayer" -> writeJs(action.fromPlayer),
          "targetSequenceNo" -> writeJs(action.targetSequenceNo),
          "shantenAfterAction" -> writeJs(action.shantenAfterAction),
          "handTilesAfterAction" -> writeJs(action.handTilesAfterAction),
          "revealedTiles" -> writeJs(action.revealedTiles),
          "note" -> writeJs(action.note)
        ),
      {
        case obj: ujson.Obj =>
          PaifuAction(
            sequenceNo = read[Int](obj("sequenceNo")),
            actor = obj.value.get("actor").fold(Option.empty[PlayerId])(read[Option[PlayerId]](_)),
            actionType = read[PaifuActionType](obj("actionType")),
            tile = obj.value.get("tile").fold(Option.empty[PaifuTile])(read[Option[PaifuTile]](_)),
            shantenAfterAction = obj.value.get("shantenAfterAction").fold(Option.empty[Int])(read[Option[Int]](_)),
            handTilesAfterAction = obj.value.get("handTilesAfterAction").fold(Option.empty[Vector[PaifuTile]])(read[Option[Vector[PaifuTile]]](_)),
            revealedTiles = obj.value.get("revealedTiles").fold(Vector.empty[PaifuTile])(read[Vector[PaifuTile]](_)),
            note = obj.value.get("note").fold(Option.empty[String])(read[Option[String]](_)),
            fromPlayer = obj.value.get("fromPlayer").fold(Option.empty[PlayerId])(read[Option[PlayerId]](_)),
            targetSequenceNo = obj.value.get("targetSequenceNo").fold(Option.empty[Int])(read[Option[Int]](_))
          )
        case json =>
          throw upickle.core.Abort(s"Expected PaifuAction object, got $json")
      }
    )
  given ReadWriter[KyokuDescriptor] =
    readwriter[ujson.Value].bimap[KyokuDescriptor](
      descriptor =>
        ujson.Obj(
          "roundWind" -> writeJs(descriptor.roundWind),
          "handNumber" -> descriptor.handNumber,
          "honba" -> descriptor.honba
        ),
      {
        case obj: ujson.Obj =>
          KyokuDescriptor(
            roundWind = read[SeatWind](obj("roundWind")),
            handNumber = read[Int](obj("handNumber")),
            honba = obj.value.get("honba").fold(0)(read[Int](_))
          )
        case json =>
          throw upickle.core.Abort(s"Expected KyokuDescriptor object, got $json")
      }
    )
  given ReadWriter[Map[PlayerId, Vector[PaifuTile]]] =
    readwriter[ujson.Value].bimap[Map[PlayerId, Vector[PaifuTile]]](
      hands =>
        ujson.Obj.from(
          hands.toSeq.map { case (playerId, tiles) =>
            playerId.value -> writeJs(tiles)
          }
        ),
      {
        case obj: ujson.Obj =>
          obj.value.map { case (playerId, tiles) =>
            PlayerId(playerId) -> read[Vector[PaifuTile]](tiles)
          }.toMap
        case json =>
          read[Vector[(PlayerId, Vector[PaifuTile])]](json).toMap
      }
    )
  given ReadWriter[PaifuHand] = macroRW
  given ReadWriter[PaifuPlayerTrack] = macroRW
  given ReadWriter[PaifuRoundPlayer] = macroRW
  given ReadWriter[PaifuTimeline] = macroRW
  given ReadWriter[PaifuRound] = macroRW
  given ReadWriter[FinalStanding] = macroRW
  given ReadWriter[PaifuMetadata] = macroRW
  given ReadWriter[Paifu] = macroRW

  private val legacyYakuKindByName: Map[String, MahjongYakuKind] =
    Map(
      "国士无双十三面" -> MahjongYakuKind.KokushiMusouThirteenWait,
      "国士无双" -> MahjongYakuKind.KokushiMusou,
      "纯正九莲宝灯" -> MahjongYakuKind.PureChuurenPoutou,
      "九莲宝灯" -> MahjongYakuKind.ChuurenPoutou,
      "字一色" -> MahjongYakuKind.Tsuuiisou,
      "绿一色" -> MahjongYakuKind.Ryuuiisou,
      "清老头" -> MahjongYakuKind.Chinroutou,
      "四暗刻单骑" -> MahjongYakuKind.SuuankouTanki,
      "四暗刻" -> MahjongYakuKind.Suuankou,
      "大三元" -> MahjongYakuKind.Daisangen,
      "大四喜" -> MahjongYakuKind.Daisuushi,
      "小四喜" -> MahjongYakuKind.Shousuushi,
      "四杠子" -> MahjongYakuKind.Suukantsu,
      "天和" -> MahjongYakuKind.Tenhou,
      "地和" -> MahjongYakuKind.Chiihou,
      "七对子" -> MahjongYakuKind.Chiitoitsu,
      "门前清自摸和" -> MahjongYakuKind.MenzenTsumo,
      "双立直" -> MahjongYakuKind.DoubleRiichi,
      "立直" -> MahjongYakuKind.Riichi,
      "一发" -> MahjongYakuKind.Ippatsu,
      "岭上开花" -> MahjongYakuKind.RinshanKaihou,
      "海底捞月" -> MahjongYakuKind.HaiteiRaoyue,
      "河底捞鱼" -> MahjongYakuKind.HouteiRaoyui,
      "流局满贯" -> MahjongYakuKind.NagashiMangan,
      "断幺九" -> MahjongYakuKind.Tanyao,
      "役牌:白" -> MahjongYakuKind.YakuhaiHaku,
      "役牌:发" -> MahjongYakuKind.YakuhaiHatsu,
      "役牌:中" -> MahjongYakuKind.YakuhaiChun,
      "场风牌" -> MahjongYakuKind.RoundWind,
      "自风牌" -> MahjongYakuKind.SeatWind,
      "平和" -> MahjongYakuKind.Pinfu,
      "二杯口" -> MahjongYakuKind.Ryanpeikou,
      "一杯口" -> MahjongYakuKind.Iipeikou,
      "对对和" -> MahjongYakuKind.Toitoi,
      "三暗刻" -> MahjongYakuKind.Sanankou,
      "三杠子" -> MahjongYakuKind.Sankantsu,
      "小三元" -> MahjongYakuKind.Shousangen,
      "三色同顺" -> MahjongYakuKind.SanshokuDoujun,
      "一气通贯" -> MahjongYakuKind.Ittsu,
      "清一色" -> MahjongYakuKind.Chinitsu,
      "混一色" -> MahjongYakuKind.Honitsu,
      "混老头" -> MahjongYakuKind.Honroutou,
      "纯全带幺九" -> MahjongYakuKind.Junchan,
      "混全带幺九" -> MahjongYakuKind.Chanta,
      "三色同刻" -> MahjongYakuKind.SanshokuDoukou,
      "宝牌" -> MahjongYakuKind.Dora,
      "红宝牌" -> MahjongYakuKind.AkaDora,
      "里宝牌" -> MahjongYakuKind.UraDora
    )

  private val legacyRoundSettlementNoteByName: Map[String, RoundSettlementNote] =
    Map(
      "荒牌流局" -> RoundSettlementNote.ExhaustiveDraw,
      "abortive draw requested" -> RoundSettlementNote.AbortiveDrawRequested,
      "三家和流局" -> RoundSettlementNote.TripleRonAbortiveDraw,
      "双响" -> RoundSettlementNote.DoubleRon,
      "三家荣和" -> RoundSettlementNote.TripleRon,
      "流局满贯" -> RoundSettlementNote.NagashiMangan,
      "满贯" -> RoundSettlementNote.Mangan,
      "跳满" -> RoundSettlementNote.Haneman,
      "倍满" -> RoundSettlementNote.Baiman,
      "三倍满" -> RoundSettlementNote.Sanbaiman,
      "役满" -> RoundSettlementNote.Yakuman,
      "双倍役满" -> RoundSettlementNote.DoubleYakuman,
      "2倍役满" -> RoundSettlementNote.DoubleYakuman,
      "3倍役满" -> RoundSettlementNote.TripleYakuman,
      "4倍役满" -> RoundSettlementNote.QuadrupleYakuman,
      "5倍役满" -> RoundSettlementNote.QuintupleYakuman,
      "6倍役满" -> RoundSettlementNote.SextupleYakuman,
      "7倍役满" -> RoundSettlementNote.SeptupleYakuman,
      "8倍役满" -> RoundSettlementNote.OctupleYakuman,
      "9倍役满" -> RoundSettlementNote.NonupleYakuman
    )

  private def readRoundSettlementNote(value: String): RoundSettlementNote =
    legacyRoundSettlementNoteByName
      .get(value)
      .orElse(Try(RoundSettlementNote.valueOf(value)).toOption)
      .getOrElse(throw upickle.core.Abort(s"Unsupported round settlement note: $value"))

  private def readYakuKind(obj: ujson.Obj): MahjongYakuKind =
    obj.value.get("kind") match
      case Some(kind) => read[MahjongYakuKind](kind)
      case None =>
        val legacyName = read[String](obj("name"))
        legacyYakuKindByName
          .get(legacyName)
          .orElse(MahjongYakuKind.values.find(_.productPrefix == legacyName))
          .getOrElse(throw upickle.core.Abort(s"Unsupported legacy yaku name: $legacyName"))
