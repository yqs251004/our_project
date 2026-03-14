package riichinexus.infrastructure.json

import java.time.Instant

import riichinexus.domain.model.*
import upickle.default.*

object JsonCodecs:
  private def stringEnumReadWriter[A](
      fromString: String => A,
      toStringValue: A => String
  ): ReadWriter[A] =
    readwriter[String].bimap[A](toStringValue, fromString)

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

  given ReadWriter[RoleKind] =
    stringEnumReadWriter(RoleKind.valueOf, _.toString)
  given ReadWriter[Permission] =
    stringEnumReadWriter(Permission.valueOf, _.toString)
  given ReadWriter[RoleGrant] = macroRW
  given ReadWriter[GuestAccessSession] = macroRW
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
  given ReadWriter[ClubRankNode] = macroRW
  given ReadWriter[ClubTitleAssignment] = macroRW
  given ReadWriter[ClubRelationKind] =
    stringEnumReadWriter(ClubRelationKind.valueOf, _.toString)
  given ReadWriter[ClubRelation] = macroRW
  given ReadWriter[ClubHonor] = macroRW
  given ReadWriter[GlobalDictionaryEntry] = macroRW
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
  given ReadWriter[SeatWind] =
    stringEnumReadWriter(SeatWind.valueOf, _.toString)
  given ReadWriter[StageLineupSeat] = macroRW
  given ReadWriter[StageLineupSubmission] = macroRW
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
  given ReadWriter[Tournament] = macroRW
  given ReadWriter[TableSeat] = macroRW
  given ReadWriter[TableStatus] =
    stringEnumReadWriter(TableStatus.valueOf, _.toString)
  given ReadWriter[AppealTableResolution] =
    stringEnumReadWriter(AppealTableResolution.valueOf, _.toString)
  given ReadWriter[Table] = macroRW
  given ReadWriter[MatchRecordSeatResult] = macroRW
  given ReadWriter[MatchRecord] = macroRW
  given ReadWriter[AppealAttachment] = macroRW
  given ReadWriter[AppealDecisionLog] = macroRW
  given ReadWriter[AppealStatus] =
    stringEnumReadWriter(AppealStatus.valueOf, _.toString)
  given ReadWriter[AppealDecisionType] =
    stringEnumReadWriter(AppealDecisionType.valueOf, _.toString)
  given ReadWriter[AppealTicket] = macroRW

  given ReadWriter[HandOutcome] =
    stringEnumReadWriter(HandOutcome.valueOf, _.toString)
  given ReadWriter[Yaku] = macroRW
  given ReadWriter[ScoreChange] = macroRW
  given ReadWriter[RoundSettlement] = macroRW
  given ReadWriter[AgariResult] = macroRW
  given ReadWriter[PaifuActionType] =
    stringEnumReadWriter(PaifuActionType.valueOf, _.toString)
  given ReadWriter[PaifuAction] = macroRW
  given ReadWriter[KyokuDescriptor] = macroRW
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
  given ReadWriter[PublicScheduleView] = macroRW
  given ReadWriter[PublicClubDirectoryEntry] = macroRW
  given ReadWriter[PlayerLeaderboardEntry] = macroRW
  given ReadWriter[ClubLeaderboardEntry] = macroRW
