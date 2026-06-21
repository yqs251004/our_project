package riichinexus.microservices.platformadmin.api
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerClubRemovalPrivateAPIMessage

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.api.`private`.{ListClubReadModelsPrivateAPIMessage, RecordClubDissolutionPrivateAPIMessage, RecordClubRelationRemovalPrivateAPIMessage, ResolveClubReadModelsPrivateAPIMessage}
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.`private`.{ResetAdvancedStatsBoardPrivateAPIMessage, ResetDashboardPrivateAPIMessage}
import riichinexus.microservices.opsanalytics.objects.DashboardOwner
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminClubView
/** 平台管理员解散俱乐部并清理关联投影。 */
final case class PlatformAdminDissolveClubAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminClubView]:

  override def plan(context: ApiPlanContext): IO[PlatformAdminClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireDissolveClubPermission(context, actor)
      dissolvedAt <- IO.realTimeInstant
      command = DissolveClubCommand(
        clubId = clubId,
        actor = actor,
        dissolvedAt = dissolvedAt
      )
      club <- resolveClub(context, command.clubId)
      _ <- IO.delay(ensureClubCanDissolve(club, command.clubId))
      _ <- removeMembersFromClub(context, club, command.clubId)
      _ <- removeRelationsToClub(context, command.clubId)
      dissolvedClub <- commitDissolvedClub(context, club, command)
      _ <- RecordAuditEventsPrivateAPIMessage(dissolveClubAudit(dissolvedClub, command)).plan(context)
      _ <- resetClubAnalytics(context, command)
    yield platformAdminClubView(dissolvedClub)

  private def requireDissolveClubPermission(context: ApiPlanContext, actor: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = actor.playerId.map(_.value),
      permission = Permission.DissolveClub
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${actor.displayName} is not allowed to dissolve club"))
    }

  private def resolveClub(
      context: ApiPlanContext,
      clubId: ClubId
  ): IO[ClubPrivateView] =
    ResolveClubReadModelsPrivateAPIMessage(Vector(clubId)).plan(context)
      .map(_.headOption.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found")))

  private def ensureClubCanDissolve(club: ClubPrivateView, clubId: ClubId): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

  private def removeMembersFromClub(context: ApiPlanContext, club: ClubPrivateView, clubId: ClubId): IO[Unit] =
    club.members.foldLeft(IO.unit) { (previous, memberId) =>
      previous.flatMap(_ => RecordPlayerClubRemovalPrivateAPIMessage(memberId, clubId).plan(context).map(_ => ()))
    }

  private def removeRelationsToClub(context: ApiPlanContext, clubId: ClubId): IO[Unit] =
    ListClubReadModelsPrivateAPIMessage(activeOnly = true).plan(context).flatMap { clubs =>
      clubs
        .filterNot(_.id == clubId)
        .filter(_.relations.exists(_.targetClubId == clubId))
        .foldLeft(IO.unit) { (previous, relatedClub) =>
          previous.flatMap(_ =>
            RecordClubRelationRemovalPrivateAPIMessage(relatedClub.id, clubId)
              .plan(context)
              .map(_ => ())
          )
        }
    }

  private def commitDissolvedClub(
      context: ApiPlanContext,
      club: ClubPrivateView,
      command: DissolveClubCommand
  ): IO[ClubPrivateView] =
    RecordClubDissolutionPrivateAPIMessage(
      command.clubId,
      command.actor.playerId.getOrElse(club.creator),
      command.dissolvedAt
    ).plan(context).flatMap {
      case Some(_) => resolveClub(context, command.clubId)
      case None    => IO.raiseError(NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    }

  private def resetClubAnalytics(
      context: ApiPlanContext,
      command: DissolveClubCommand
  ): IO[Unit] =
    val clubOwner = DashboardOwner.Club(command.clubId)
    ResetDashboardPrivateAPIMessage(clubOwner, command.dissolvedAt).plan(context).flatMap(_ =>
      ResetAdvancedStatsBoardPrivateAPIMessage(clubOwner, command.dissolvedAt).plan(context).map(_ => ())
    )

  private def dissolveClubAudit(club: ClubPrivateView, command: DissolveClubCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = AuditEventType.ClubDissolved,
        occurredAt = command.dissolvedAt,
        actorId = command.actor.playerId,
        details = Map("memberCount" -> club.members.size.toString),
        note = Some(s"Club ${command.clubId.value} dissolved")
      )
    )

  private def platformAdminClubView(club: ClubPrivateView): PlatformAdminClubView =
    PlatformAdminClubView(
      clubId = club.id.value,
      name = club.name,
      creator = club.creator.value,
      createdAt = club.createdAt.toString,
      memberCount = club.members.size,
      adminCount = club.admins.size,
      totalPoints = club.totalPoints,
      powerRating = club.powerRating,
      dissolvedAt = club.dissolvedAt.map(_.toString),
      dissolvedBy = club.dissolvedBy.map(_.value)
    )

  /** 平台解散俱乐部流程中使用的已授权命令参数。 */
  private final case class DissolveClubCommand(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      dissolvedAt: Instant
  )
