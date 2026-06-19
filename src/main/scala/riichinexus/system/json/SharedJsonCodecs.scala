package riichinexus.system.json

import java.time.Instant
import scala.annotation.targetName

import riichinexus.microservices.audit.domain.auditevent.{AuditEvent, AuditEventId}
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifu.PaifuId
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.finalization.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import upickle.default.{ReadWriter, macroRW, read, readwriter, writeJs}

object SharedJsonCodecs:
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

  given ReadWriter[AuditEvent] = macroRW
