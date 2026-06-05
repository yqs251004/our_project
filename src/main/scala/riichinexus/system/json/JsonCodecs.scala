package riichinexus.system.json
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission

import riichinexus.microservices.tournament.objects.paifumanagement.{AgariResult, AgariWinResult, FinalStanding, HandOutcome, KyokuDescriptor, MahjongYakuKind, Paifu, PaifuAction, PaifuActionType, PaifuHand, PaifuMetadata, PaifuPlayerTrack, PaifuRound, PaifuRoundPlayer, PaifuTimeline, PaifuTile, RoundSettlement, ScoreChange, Yaku}
import riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus

import java.time.Instant
import scala.annotation.targetName

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.{Role, SessionPrincipalKind}
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.rankprivilegemanagement.{ClubPrivilegeCode, ClubRankNode}
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.opsanalytics.objects.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.{PlayerStatus, RankPlatform, RankSnapshot}
import riichinexus.microservices.tournament.appeal.domain.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.PaifuTileFunctions
import riichinexus.microservices.tournament.appeal.objects.AppealDecisionLog
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.{KnockoutLane, KnockoutRuleConfig}
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableSeat, TableStatus}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentFormat, TournamentParticipantKind, TournamentStatus, TournamentWhitelistEntry}
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.settlementmanagement.{TournamentSettlementAdjustment, TournamentSettlementEntry}
import upickle.default.*

object JsonCodecs:
  private def stringEnumReadWriter[A](
      fromString: String => A,
      toStringValue: A => String
  ): ReadWriter[A] =
    readwriter[String].bimap[A](toStringValue, fromString)

  private def eitherStringEnumReadWriter[A](
      fromString: String => Either[String, A],
      toStringValue: A => String
  ): ReadWriter[A] =
    stringEnumReadWriter(
      value => fromString(value).fold(message => throw IllegalArgumentException(message), identity),
      toStringValue
    )

  given [A: ReadWriter]: ReadWriter[Option[A]] =
    readwriter[ujson.Value].bimap[Option[A]](
      _.map(writeJs(_)).getOrElse(ujson.Null),
      {
        case ujson.Null => None
        case arr: ujson.Arr if arr.value.isEmpty => None
        case arr: ujson.Arr if arr.value.size == 1 => Some(read[A](arr.value.head))
        case json => Some(read[A](json))
      }
    )

  @targetName("givenReadWriterOptionVector")
  given [A: ReadWriter]: ReadWriter[Option[Vector[A]]] =
    readwriter[ujson.Value].bimap[Option[Vector[A]]](
      _.map(writeJs(_)).getOrElse(ujson.Null),
      {
        case ujson.Null => None
        case arr: ujson.Arr => Some(read[Vector[A]](arr))
        case json => Some(Vector(read[A](json)))
      }
    )

  given ReadWriter[Instant] =
    readwriter[String].bimap[Instant](_.toString, Instant.parse)

  given ReadWriter[PlayerId] =
    readwriter[String].bimap[PlayerId](_.value, PlayerId(_))
  given ReadWriter[ClubId] =
    readwriter[String].bimap[ClubId](_.value, ClubId(_))
  given ReadWriter[TournamentId] =
    readwriter[String].bimap[TournamentId](_.value, TournamentId(_))
  given ReadWriter[TournamentStageId] =
    readwriter[String].bimap[TournamentStageId](_.value, TournamentStageId(_))
  given ReadWriter[TableId] =
    readwriter[String].bimap[TableId](_.value, TableId(_))
  given ReadWriter[PaifuId] =
    readwriter[String].bimap[PaifuId](_.value, PaifuId(_))
  given ReadWriter[MatchRecordId] =
    readwriter[String].bimap[MatchRecordId](_.value, MatchRecordId(_))
  given ReadWriter[AppealTicketId] =
    readwriter[String].bimap[AppealTicketId](_.value, AppealTicketId(_))
  given ReadWriter[MembershipApplicationId] =
    readwriter[String].bimap[MembershipApplicationId](_.value, MembershipApplicationId(_))
  given ReadWriter[LineupSubmissionId] =
    readwriter[String].bimap[LineupSubmissionId](_.value, LineupSubmissionId(_))
  given ReadWriter[GuestSessionId] =
    readwriter[String].bimap[GuestSessionId](_.value, GuestSessionId(_))
  given ReadWriter[SettlementSnapshotId] =
    readwriter[String].bimap[SettlementSnapshotId](_.value, SettlementSnapshotId(_))
  given ReadWriter[AuditEventId] =
    readwriter[String].bimap[AuditEventId](_.value, AuditEventId(_))
  given ReadWriter[AdvancedStatsRecomputeTaskId] =
    readwriter[String].bimap[AdvancedStatsRecomputeTaskId](_.value, AdvancedStatsRecomputeTaskId(_))

  given ReadWriter[Role] =
    eitherStringEnumReadWriter(Role.fromString, Role.toString)
  given ReadWriter[Permission] =
    stringEnumReadWriter(Permission.valueOf, _.toString)
  given ReadWriter[RoleGrant] = macroRW
  given ReadWriter[AccountCredential] = macroRW
  given ReadWriter[GuestAccessSession] = macroRW
  given ReadWriter[SessionPrincipalKind] =
    eitherStringEnumReadWriter(
      SessionPrincipalKind.fromString,
      SessionPrincipalKind.toString
    )
  given ReadWriter[AuthenticatedSession] = macroRW
  given ReadWriter[AccessPrincipal] = macroRW

  given ReadWriter[RankPlatform] =
    eitherStringEnumReadWriter(
      RankPlatform.fromString,
      RankPlatform.toString
    )
  given ReadWriter[RankSnapshot] = macroRW
  given ReadWriter[PlayerStatus] =
    eitherStringEnumReadWriter(
      PlayerStatus.fromString,
      PlayerStatus.toString
    )
  given ReadWriter[Player] = macroRW

  given ReadWriter[ClubApplicationStatus] =
    stringEnumReadWriter(
      ClubApplicationStatus.fromString,
      ClubApplicationStatus.toString
    )
  given ReadWriter[ClubMembershipApplication] =
    readwriter[ujson.Value].bimap[ClubMembershipApplication](
      application =>
        ujson.Obj.from(
          Vector(
            "id" -> writeJs(application.id),
            "playerId" -> writeJs(application.playerId),
            "displayName" -> writeJs(application.displayName),
            "submittedAt" -> writeJs(application.submittedAt),
            "message" -> writeJs(application.message),
            "status" -> writeJs(application.status),
            "reviewedBy" -> writeJs(application.reviewedBy),
            "reviewedAt" -> writeJs(application.reviewedAt),
            "reviewNote" -> writeJs(application.reviewNote),
            "withdrawnByPrincipalId" -> writeJs(application.withdrawnByPrincipalId)
          ) ++ application.applicantUserId.map("applicantUserId" -> writeJs(_)).toVector
        ),
      {
        case obj: ujson.Obj =>
          def optional[A: ReadWriter](name: String): Option[A] =
            obj.value.get(name).fold(Option.empty[A])(read[Option[A]](_))

          ClubMembershipApplication(
            id = read[MembershipApplicationId](obj("id")),
            playerId = optional[PlayerId]("playerId"),
            applicantUserId = optional[String]("applicantUserId"),
            displayName = read[String](obj("displayName")),
            submittedAt = read[Instant](obj("submittedAt")),
            message = optional[String]("message"),
            status = obj.value.get("status").fold(ClubApplicationStatus.Pending)(read[ClubApplicationStatus](_)),
            reviewedBy = optional[PlayerId]("reviewedBy"),
            reviewedAt = optional[Instant]("reviewedAt"),
            reviewNote = optional[String]("reviewNote"),
            withdrawnByPrincipalId = optional[String]("withdrawnByPrincipalId")
          )
        case json =>
          throw upickle.core.Abort(s"Expected ClubMembershipApplication object, got $json")
      }
    )
  given ReadWriter[ClubRankNode] = macroRW
  given ReadWriter[ClubMemberContribution] = macroRW
  given ReadWriter[ClubMemberPrivilegeSnapshot] = macroRW
  given ReadWriter[ClubTitleAssignment] = macroRW
  given ReadWriter[ClubRelationKind] =
    stringEnumReadWriter(ClubRelationKind.fromString, ClubRelationKind.toString)
  given ReadWriter[ClubRelation] = macroRW
  given ReadWriter[ClubRecruitmentPolicy] = macroRW
  given ReadWriter[ClubHonor] = macroRW
  given ReadWriter[AuditEvent] = macroRW
  given ReadWriter[Club] = macroRW

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
            notes = obj.value.get("notes").fold(Vector.empty[String])(read[Vector[String]](_))
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

  private def readYakuKind(obj: ujson.Obj): MahjongYakuKind =
    obj.value.get("kind") match
      case Some(kind) => read[MahjongYakuKind](kind)
      case None =>
        val legacyName = read[String](obj("name"))
        legacyYakuKindByName
          .get(legacyName)
          .orElse(MahjongYakuKind.values.find(_.productPrefix == legacyName))
          .getOrElse(throw upickle.core.Abort(s"Unsupported legacy yaku name: $legacyName"))

  given ReadWriter[DashboardOwner] =
    eitherStringEnumReadWriter(
      DashboardOwner.fromString,
      DashboardOwner.toString
    )
  given ReadWriter[Dashboard] = macroRW
  given ReadWriter[AdvancedStatsBoard] = macroRW
  given ReadWriter[AdvancedStatsRecomputeTaskStatus] =
    eitherStringEnumReadWriter(
      AdvancedStatsRecomputeTaskStatus.fromString,
      AdvancedStatsRecomputeTaskStatus.toString
    )
  given ReadWriter[AdvancedStatsBackfillMode] =
    eitherStringEnumReadWriter(
      AdvancedStatsBackfillMode.fromString,
      AdvancedStatsBackfillMode.toString
    )
  given ReadWriter[AdvancedStatsRecomputeTask] = macroRW
  given ReadWriter[AdvancedStatsTaskQueueSummary] = macroRW
