package riichinexus.infrastructure.json

import java.time.Instant
import scala.annotation.targetName

import riichinexus.domain.event.*
import riichinexus.domain.model.*
import upickle.default.*

object JsonCodecs:
  private def stringEnumReadWriter[A](
      fromString: String => A,
      toStringValue: A => String
  ): ReadWriter[A] =
    readwriter[String].bimap[A](toStringValue, fromString)

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
  given ReadWriter[EventCascadeRecordId] =
    readwriter[String].bimap[EventCascadeRecordId](_.value, EventCascadeRecordId(_))
  given ReadWriter[DomainEventOutboxRecordId] =
    readwriter[String].bimap[DomainEventOutboxRecordId](_.value, DomainEventOutboxRecordId(_))
  given ReadWriter[DomainEventDeliveryReceiptId] =
    readwriter[String].bimap[DomainEventDeliveryReceiptId](_.value, DomainEventDeliveryReceiptId(_))
  given ReadWriter[DomainEventSubscriberCursorId] =
    readwriter[String].bimap[DomainEventSubscriberCursorId](_.value, DomainEventSubscriberCursorId(_))

  given ReadWriter[RoleKind] =
    stringEnumReadWriter(RoleKind.valueOf, _.toString)
  given ReadWriter[Permission] =
    stringEnumReadWriter(Permission.valueOf, _.toString)
  given ReadWriter[RoleGrant] = macroRW
  given ReadWriter[AccountCredential] = macroRW
  given ReadWriter[GuestAccessSession] = macroRW
  given ReadWriter[AuthenticatedSession] = macroRW
  given ReadWriter[AccessPrincipal] = macroRW

  given ReadWriter[RankPlatform] =
    stringEnumReadWriter(RankPlatform.valueOf, _.toString)
  given ReadWriter[RankSnapshot] = macroRW
  given ReadWriter[PlayerStatus] =
    stringEnumReadWriter(PlayerStatus.valueOf, _.toString)
  given ReadWriter[Player] = macroRW

  given ReadWriter[ClubMembershipApplicationStatus] =
    stringEnumReadWriter(ClubMembershipApplicationStatus.valueOf, _.toString)
  given ReadWriter[ClubMembershipApplication] = macroRW
  given ReadWriter[ClubPrivilegeCode] =
    stringEnumReadWriter(ClubPrivilegeCode.valueOf, _.toString)
  given ReadWriter[ClubPrivilegeDefinition] = macroRW
  given ReadWriter[ClubRankNode] = macroRW
  given ReadWriter[ClubMemberContribution] = macroRW
  given ReadWriter[ClubMemberPrivilegeSnapshot] = macroRW
  given ReadWriter[ClubTitleAssignment] = macroRW
  given ReadWriter[ClubRelationKind] =
    stringEnumReadWriter(ClubRelationKind.valueOf, _.toString)
  given ReadWriter[ClubRelation] = macroRW
  given ReadWriter[ClubRecruitmentPolicy] = macroRW
  given ReadWriter[ClubHonor] = macroRW
  given ReadWriter[GlobalDictionaryEntry] = macroRW
  given ReadWriter[GlobalDictionaryValueType] =
    stringEnumReadWriter(GlobalDictionaryValueType.valueOf, _.toString)
  given ReadWriter[GlobalDictionarySchemaEntry] = macroRW
  given ReadWriter[GlobalDictionarySchema] = macroRW
  given ReadWriter[DictionaryNamespaceReviewStatus] =
    stringEnumReadWriter(DictionaryNamespaceReviewStatus.valueOf, _.toString)
  given ReadWriter[DictionaryNamespaceRegistration] = macroRW
  given ReadWriter[DictionaryNamespaceReminderKind] =
    stringEnumReadWriter(DictionaryNamespaceReminderKind.valueOf, _.toString)
  given ReadWriter[DictionaryNamespaceReminderAction] = macroRW
  given ReadWriter[AuditEventEntry] = macroRW
  given ReadWriter[Club] = macroRW

  given ReadWriter[TournamentStatus] =
    stringEnumReadWriter(TournamentStatus.valueOf, _.toString)
  given ReadWriter[StageFormat] =
    stringEnumReadWriter(StageFormat.valueOf, _.toString)
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
    stringEnumReadWriter(SeatWind.valueOf, _.toString)
  given ReadWriter[StageLineupSeat] = macroRW
  given ReadWriter[StageLineupSubmission] = macroRW
  given ReadWriter[StageTablePlan] = macroRW
  given ReadWriter[TournamentParticipantKind] =
    stringEnumReadWriter(TournamentParticipantKind.valueOf, _.toString)
  given ReadWriter[TournamentWhitelistEntry] = macroRW
  given ReadWriter[TournamentStage] = macroRW
  given ReadWriter[StageStandingEntry] = macroRW
  given ReadWriter[StageRankingSnapshot] = macroRW
  given ReadWriter[StageAdvancementSnapshot] = macroRW
  given ReadWriter[KnockoutBracketSlot] = macroRW
  given ReadWriter[KnockoutBracketResult] = macroRW
  given ReadWriter[KnockoutBracketMatch] = macroRW
  given ReadWriter[KnockoutBracketRound] = macroRW
  given ReadWriter[KnockoutBracketSnapshot] = macroRW
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
  given ReadWriter[Yaku] = macroRW
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
            tile = obj.value.get("tile").fold(Option.empty[String])(read[Option[String]](_)),
            shantenAfterAction = obj.value.get("shantenAfterAction").fold(Option.empty[Int])(read[Option[Int]](_)),
            handTilesAfterAction = obj.value.get("handTilesAfterAction").fold(Option.empty[Vector[String]])(read[Option[Vector[String]]](_)),
            revealedTiles = obj.value.get("revealedTiles").fold(Vector.empty[String])(read[Vector[String]](_)),
            note = obj.value.get("note").fold(Option.empty[String])(read[Option[String]](_))
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
  given ReadWriter[Map[PlayerId, Vector[String]]] =
    readwriter[ujson.Value].bimap[Map[PlayerId, Vector[String]]](
      hands =>
        ujson.Obj.from(
          hands.toSeq.map { case (playerId, tiles) =>
            playerId.value -> writeJs(tiles)
          }
        ),
      {
        case obj: ujson.Obj =>
          obj.value.map { case (playerId, tiles) =>
            PlayerId(playerId) -> read[Vector[String]](tiles)
          }.toMap
        case json =>
          read[Vector[(PlayerId, Vector[String])]](json).toMap
      }
    )
  given ReadWriter[KyokuRecord] = macroRW
  given ReadWriter[FinalStanding] = macroRW
  given ReadWriter[PaifuMetadata] = macroRW
  given ReadWriter[Paifu] = macroRW

  given ReadWriter[DashboardOwner] =
    readwriter[String].bimap[DashboardOwner](
      {
        case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
        case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"
      },
      { raw =>
        raw.split(":", 2).toList match
          case "player" :: value :: Nil => DashboardOwner.Player(PlayerId(value))
          case "club" :: value :: Nil   => DashboardOwner.Club(ClubId(value))
          case _ =>
            throw IllegalArgumentException(s"Unsupported dashboard owner value: $raw")
      }
    )
  given ReadWriter[Dashboard] = macroRW
  given ReadWriter[AdvancedStatsBoard] = macroRW
  given ReadWriter[AdvancedStatsRecomputeTaskStatus] =
    stringEnumReadWriter(AdvancedStatsRecomputeTaskStatus.valueOf, _.toString)
  given ReadWriter[AdvancedStatsBackfillMode] =
    stringEnumReadWriter(AdvancedStatsBackfillMode.valueOf, _.toString)
  given ReadWriter[AdvancedStatsRecomputeTask] = macroRW
  given ReadWriter[AdvancedStatsTaskQueueSummary] = macroRW
  given ReadWriter[EventCascadeConsumer] =
    stringEnumReadWriter(EventCascadeConsumer.valueOf, _.toString)
  given ReadWriter[EventCascadeStatus] =
    stringEnumReadWriter(EventCascadeStatus.valueOf, _.toString)
  given ReadWriter[EventCascadeRecord] = macroRW
  given ReadWriter[DomainEventOutboxStatus] =
    stringEnumReadWriter(DomainEventOutboxStatus.valueOf, _.toString)
  given ReadWriter[DomainEventOutboxRecord] = macroRW
  given ReadWriter[DomainEventDeliveryReceipt] = macroRW
  given ReadWriter[DomainEventSubscriberCursor] = macroRW
  given ReadWriter[DomainEventBusSummary] = macroRW
  given ReadWriter[DomainEventSubscriberStatus] = macroRW
  given ReadWriter[DomainEventSubscriberPartitionStatus] = macroRW
  given ReadWriter[DomainEventOutboxOperationFailure] = macroRW
  given ReadWriter[DomainEventOutboxBatchOperationResult] = macroRW
  given ReadWriter[MatchRecordArchived] = macroRW
  given ReadWriter[AppealTicketFiled] = macroRW
  given ReadWriter[AppealTicketResolved] = macroRW
  given ReadWriter[AppealTicketWorkflowUpdated] = macroRW
  given ReadWriter[AppealTicketReopened] = macroRW
  given ReadWriter[AppealTicketAdjudicated] = macroRW
  given ReadWriter[TournamentSettlementRecorded] = macroRW
  given ReadWriter[GlobalDictionaryUpdated] = macroRW
  given ReadWriter[PlayerBanned] = macroRW
  given ReadWriter[ClubDissolved] = macroRW
  given ReadWriter[DomainEvent] = macroRW
