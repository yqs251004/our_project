package riichinexus.microservices.platformadmin.api

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerClubRemovalPrivateAPIMessage

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.api.audit.`private`.ListClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.api.audit.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.api.profile.`private`.RecordClubDissolutionPrivateAPIMessage
import riichinexus.microservices.club.api.relation.`private`.RecordClubRelationRemovalPrivateAPIMessage
import riichinexus.microservices.club.objects.profile.`private`.ClubPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.`private`.ResetAdvancedStatsBoardPrivateAPIMessage
import riichinexus.microservices.opsanalytics.api.`private`.ResetDashboardPrivateAPIMessage
import riichinexus.microservices.opsanalytics.objects.DashboardOwner
import riichinexus.microservices.platformadmin.objects.PlatformAdminClubView
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
      club <- resolveClub(context, clubId)
      _ <- IO.delay(ensureClubCanDissolve(club, clubId))
      _ <- removeMembersFromClub(context, club, clubId)
      _ <- removeRelationsToClub(context, clubId)
      dissolvedClub <- commitDissolvedClub(context, club, clubId, actor, dissolvedAt)
      _ <- RecordAuditEventsPrivateAPIMessage(dissolveClubAudit(dissolvedClub, clubId, actor, dissolvedAt)).plan(context)
      _ <- resetClubAnalytics(context, clubId, dissolvedAt)
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
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      dissolvedAt: Instant
  ): IO[ClubPrivateView] =
    RecordClubDissolutionPrivateAPIMessage(
      clubId,
      actor.playerId.getOrElse(club.creator),
      dissolvedAt
    ).plan(context).flatMap {
      case Some(_) => resolveClub(context, clubId)
      case None    => IO.raiseError(NoSuchElementException(s"Club ${clubId.value} was not found"))
    }

  private def resetClubAnalytics(
      context: ApiPlanContext,
      clubId: ClubId,
      dissolvedAt: Instant
  ): IO[Unit] =
    val clubOwner = DashboardOwner.Club(clubId)
    ResetDashboardPrivateAPIMessage(clubOwner, dissolvedAt).plan(context).flatMap(_ =>
      ResetAdvancedStatsBoardPrivateAPIMessage(clubOwner, dissolvedAt).plan(context).map(_ => ())
    )

  private def dissolveClubAudit(
      club: ClubPrivateView,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      dissolvedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = clubId.value,
        eventType = AuditEventType.ClubDissolved,
        occurredAt = dissolvedAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.MemberCount) -> club.members.size.toString),
        note = Some(s"Club ${clubId.value} dissolved")
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
