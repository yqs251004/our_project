package riichinexus.microservices.club.api.membership

import riichinexus.system.objects.`private`.StructuredEventField

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
import riichinexus.microservices.club.domain.membership.model.ClubTitleAssignment
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
/** 清除俱乐部成员头衔。 */
final case class ClearClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      clearedAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      requestedPlayerId = PlayerId(playerId)
      cleared <- clearTitle(context, requestedClubId, requestedPlayerId, actor).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      (clearedClub, existingAssignment) = cleared
      _ <- RecordAuditEventsPrivateAPIMessage(clearTitleAudit(requestedClubId, requestedPlayerId, actor, note, clearedAt, existingAssignment)).plan(context)
    yield ClubViewFunctions.clubView(clearedClub)

  private def clearTitle(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): IO[Option[(Club, ClubTitleAssignment)]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId))
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${playerId.value} was not found")))
      cleared <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureTitleCanBeCleared(club, player, clubId, playerId, actor)
          val existingAssignment = resolveExistingAssignment(club, clubId, playerId)
          IO.blocking(
            Some(
              (commitTitleClear(connection, club, playerId), existingAssignment)
            )
          )
    yield cleared

  private def ensureTitleCanBeCleared(
      club: Club,
      player: PlayerPrivateView,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"PlayerPrivateView ${playerId.value} cannot clear club title")
    ClubAuthorization.requireClubMember(club, playerId, "clear internal title")
    ClubAuthorization.requireClubAdmin(actor = actor,
      club = club,
      permission = Permission.SetClubTitle
    )

  private def resolveExistingAssignment(
      club: Club,
      clubId: ClubId,
      playerId: PlayerId
  ): ClubTitleAssignment =
    club.titleAssignments.find(_.playerId == playerId)
      .getOrElse(
        throw NoSuchElementException(
          s"PlayerPrivateView ${playerId.value} does not hold a title in club ${clubId.value}"
        )
      )

  private def commitTitleClear(
      connection: java.sql.Connection,
      club: Club,
      playerId: PlayerId
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.clearInternalTitle(club, playerId))

  private def clearTitleAudit(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      note: Option[String],
      clearedAt: Instant,
      existingAssignment: ClubTitleAssignment
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = clubId.value,
        eventType = AuditEventType.ClubTitleCleared,
        occurredAt = clearedAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value,
          StructuredEventField.toString(StructuredEventField.Title) -> existingAssignment.title
        ),
        note = note
      )
    )

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

