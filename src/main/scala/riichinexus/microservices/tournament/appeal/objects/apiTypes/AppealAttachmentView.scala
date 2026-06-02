package riichinexus.microservices.tournament.appeal.objects.apiTypes

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
import riichinexus.microservices.tournament.appeal.domain.model.AppealAttachment
import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class AppealAttachmentView(
    name: String,
    uri: String,
    contentType: Option[String],
    storageKind: AppealAttachmentStorageKind,
    mediaKind: AppealAttachmentMediaKind,
    sizeBytes: Option[Long],
    uploadedAt: Option[String]
)

object AppealAttachmentView:
  def fromDomain(attachment: AppealAttachment): AppealAttachmentView =
    AppealAttachmentView(
      name = attachment.name,
      uri = attachment.uri,
      contentType = attachment.contentType,
      storageKind = AppealAttachmentStorageKind.fromDomain(attachment.storageKind),
      mediaKind = AppealAttachmentMediaKind.fromDomain(attachment.mediaKind),
      sizeBytes = attachment.sizeBytes,
      uploadedAt = attachment.uploadedAt.map(_.toString)
    )

  given ReadWriter[AppealAttachmentView] = macroRW
