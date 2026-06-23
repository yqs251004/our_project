package riichinexus.microservices.club.api.membership

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.model.ClubMemberContribution
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.notification.api.`private`.RecordNotificationPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
/** 调整俱乐部成员贡献值。 */
final case class AdjustClubMemberContributionAPIMessage(
    clubId: String,
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      requestedPlayerId = PlayerId(playerId)
      savedClub <- adjustMemberContribution(context, requestedClubId, requestedPlayerId, actor, delta, note, occurredAt).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(adjustMemberContributionAudit(savedClub, requestedPlayerId, actor, delta, note, occurredAt)).plan(context)
      _ <- RecordNotificationPrivateAPIMessage(adjustMemberContributionNotification(savedClub, requestedPlayerId, delta)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def adjustMemberContribution(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId))
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureContributionCanBeAdjusted(club, player, playerId, actor)
          val nextContribution = resolveNextContribution(club, playerId, delta)
          val updatedBy = actor.playerId.getOrElse(club.creator)
          IO.blocking(Some(commitContributionAdjustment(connection, club, playerId, note, occurredAt, nextContribution, updatedBy)))
    yield savedClub

  private def ensureContributionCanBeAdjusted(
      club: Club,
      player: PlayerPrivateView,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"PlayerPrivateView ${playerId.value} cannot receive club contribution updates")
    ClubAuthorization.requireClubMember(club, playerId, "adjust contribution")
    ClubAuthorization.requireClubAdmin(actor = actor,
      club = club,
      permission = Permission.ManageClubOperations
    )

  private def resolveNextContribution(
      club: Club,
      playerId: PlayerId,
      delta: Int
  ): Int =
    val nextContribution = ClubFunctions.contributionOf(club, playerId) + delta
    require(nextContribution >= 0, s"Club member contribution for ${playerId.value} cannot be negative")
    nextContribution

  private def commitContributionAdjustment(
      connection: java.sql.Connection,
      club: Club,
      playerId: PlayerId,
      note: Option[String],
      occurredAt: Instant,
      nextContribution: Int,
      updatedBy: PlayerId
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(
      connection,
      ClubFunctions.updateMemberContribution(club,
          ClubMemberContribution(
            playerId = playerId,
            amount = nextContribution,
            updatedAt = occurredAt,
            updatedBy = updatedBy,
            note = note
          )
        )
    )

  private def adjustMemberContributionAudit(
      updatedClub: Club,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = updatedClub.id.value,
        eventType = AuditEventType.ClubMemberContributionAdjusted,
        occurredAt = occurredAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value,
          StructuredEventField.toString(StructuredEventField.Delta) -> delta.toString,
          StructuredEventField.toString(StructuredEventField.Contribution) -> ClubFunctions.contributionOf(updatedClub, playerId).toString,
          StructuredEventField.toString(StructuredEventField.RankCode) -> ClubFunctions.rankFor(updatedClub, playerId).map(_.code).getOrElse("unknown")
        ),
        note = note
      )
    )

  private def adjustMemberContributionNotification(
      updatedClub: Club,
      playerId: PlayerId,
      delta: Int
  ): CreateNotificationRequest =
    val nextContribution = ClubFunctions.contributionOf(updatedClub, playerId)
    val deltaText =
      if delta > 0 then s"+${delta}"
      else delta.toString
    CreateNotificationRequest(
      recipientPlayerId = playerId.value,
      notificationType = NotificationType.ClubMemberContributionAdjusted,
      title = "俱乐部贡献值已调整",
      body = s"你在 ${updatedClub.name} 的贡献值调整了 $deltaText，当前贡献值为 $nextContribution。",
      severity = Some(NotificationSeverity.Info),
      sourceService = NotificationSourceService.Club,
      sourceType = NotificationSourceType.ClubContribution,
      sourceId = updatedClub.id.value,
      actionUrl = Some(s"/public/clubs/${updatedClub.id.value}"),
      objects = Map(
        StructuredEventField.toString(StructuredEventField.ClubId) -> updatedClub.id.value,
        StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value,
        StructuredEventField.toString(StructuredEventField.Delta) -> delta.toString,
        StructuredEventField.toString(StructuredEventField.Contribution) -> nextContribution.toString
      )
    )

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

