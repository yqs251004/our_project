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

  given ReadWriter[RankPlatform] =
    stringEnumReadWriter(RankPlatform.valueOf, _.toString)
  given ReadWriter[RankSnapshot] = macroRW
  given ReadWriter[Player] = macroRW
  given ReadWriter[ClubHonor] = macroRW
  given ReadWriter[Club] = macroRW

  given ReadWriter[TournamentStatus] =
    stringEnumReadWriter(TournamentStatus.valueOf, _.toString)
  given ReadWriter[StageFormat] =
    stringEnumReadWriter(StageFormat.valueOf, _.toString)
  given ReadWriter[StageStatus] =
    stringEnumReadWriter(StageStatus.valueOf, _.toString)
  given ReadWriter[TournamentStage] = macroRW
  given ReadWriter[Tournament] = macroRW
  given ReadWriter[SeatWind] =
    stringEnumReadWriter(SeatWind.valueOf, _.toString)
  given ReadWriter[TableSeat] = macroRW
  given ReadWriter[TableStatus] =
    stringEnumReadWriter(TableStatus.valueOf, _.toString)
  given ReadWriter[Table] = macroRW

  given ReadWriter[HandOutcome] =
    stringEnumReadWriter(HandOutcome.valueOf, _.toString)
  given ReadWriter[Yaku] = macroRW
  given ReadWriter[ScoreChange] = macroRW
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
