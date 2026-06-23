package riichinexus.microservices.tournament.api.stage

import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType, StructuredEventField}
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.tournament.api.competition.TournamentGetAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageFunctions
import riichinexus.microservices.tournament.domain.stage.model.{StageLineupSeat, StageLineupSubmission, TournamentStage}
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.club.api.rankprivilege.`private`.CheckClubMemberPrivilegePrivateAPIMessage
import riichinexus.microservices.club.api.audit.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.profile.`private`.ClubPrivateView
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode

import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.system.realtime.objects.{RealtimeEvent, RealtimeEventType, RealtimeSourceEventType}

import riichinexus.microservices.tournament.objects.stage.table.SeatWind
import riichinexus.microservices.tournament.objects.stage.lineup.apiTypes.{StageLineupSeatRequest, SubmitStageLineupRequest}
import riichinexus.microservices.tournament.objects.competition.TournamentMutationView
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

/** 提交俱乐部在赛事阶段的出场阵容。 */
final case class TournamentStageSubmitLineupAPIMessage(tournamentId: String, stageId: String, request: SubmitStageLineupRequest) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      submittedAt <- IO.realTimeInstant
      submission <- IO.blocking(stageLineupSubmission(request, submittedAt))
      requestedTournamentId = TournamentId(tournamentId)
      requestedStageId = TournamentStageId(stageId)
      savedTournament <- submitLineup(context, requestedTournamentId, requestedStageId, submission, actor)
        .map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- publishLineupSubmitted(context, requestedTournamentId, submission)
      _ <- RecordBulkNotificationsPrivateAPIMessage(lineupSelectedNotifications(savedTournament, requestedStageId, submission)).plan(context)
      detail <- TournamentGetAPIMessage(requestedTournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def publishLineupSubmitted(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      submission: StageLineupSubmission
  ): IO[Unit] =
    context.realtimeEventBus.publish(
        RealtimeEvent(
          id = submission.id.value,
          eventType = RealtimeEventType.TournamentChanged,
          aggregateType = AggregateType.toString(AggregateType.Tournament),
          aggregateId = tournamentId.value,
        occurredAt = submission.submittedAt,
        sourceEventType = RealtimeSourceEventType.fromString(AuditEventType.TournamentLineupSubmitted.toString),
        actorId = Some(submission.submittedBy.value),
        actionUrl = Some(s"/public/tournaments/${tournamentId.value}")
      )
    )

  private def submitLineup(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipalPrivateView
  ): IO[Option[Tournament]] =
    loadTournament(context, tournamentId).flatMap {
      case Some(tournament) => submitLineupForTournament(context, tournament, tournamentId, stageId, submission, actor).map(Some(_))
      case None             => IO.pure(None)
    }

  private def submitLineupForTournament(
      context: ApiPlanContext,
      tournament: Tournament,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipalPrivateView
  ): IO[Tournament] =
    for
      stage <- IO.blocking(resolveWritableStage(tournament, stageId, submission))
      club <- resolveActiveClub(context, submission.clubId)
      _ <- requireClubLineupCapability(context, actor, club)
      _ <- ensureSubmitterMatchesActor(context, actor, submission)
      _ <- IO.blocking(ensureClubRegistered(tournament, tournamentId, submission))
      _ <- ensureLineupPlayersActiveMembers(context, club, submission)
      savedTournament <- saveLineupSubmission(context, tournament, stage, stageId, submission)
    yield savedTournament

  private def loadTournament(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): IO[Option[Tournament]] =
    IO.blocking(
      TournamentTable.findById(context.connection, tournamentId)
    )

  private def resolveWritableStage(
      tournament: Tournament,
      stageId: TournamentStageId,
      submission: StageLineupSubmission
  ): TournamentStage =
    val stage = requireStage(tournament, stageId)
    ensureNoLineupConflict(stage, stageId, submission)
    stage

  private def saveLineupSubmission(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage,
      stageId: TournamentStageId,
      submission: StageLineupSubmission
  ): IO[Tournament] =
    IO.blocking {
      TournamentTable.save(
        context.connection,
        TournamentFunctions.updateStage(
          tournament,
          stageId,
          _ => TournamentStageFunctions.submitLineup(stage, submission)
        )
      )
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def lineupSelectedNotifications(
      tournament: Tournament,
      stageId: TournamentStageId,
      submission: StageLineupSubmission
  ): Vector[CreateNotificationRequest] =
    val stage = requireStage(tournament, stageId)
    submission.seats.map { seat =>
      val roleText = if seat.reserve then "候补" else "正选"
      CreateNotificationRequest(
        recipientPlayerId = seat.playerId.value,
        notificationType = NotificationType.TournamentLineupSelected,
        title = if seat.reserve then "被列入赛事候补阵容" else "被选中参加赛事",
        body = s"你被选入赛事 ${tournament.name} 的 ${stage.name}，身份为${roleText}选手。",
        severity = Some(NotificationSeverity.Info),
        sourceService = NotificationSourceService.Tournament,
        sourceType = NotificationSourceType.TournamentLineup,
        sourceId = submission.id.value,
        actionUrl = Some(s"/public/tournaments/${tournament.id.value}"),
        objects = Map(
          StructuredEventField.toString(StructuredEventField.TournamentId) -> tournament.id.value,
          StructuredEventField.toString(StructuredEventField.StageId) -> stage.id.value,
          StructuredEventField.toString(StructuredEventField.ClubId) -> submission.clubId.value,
          StructuredEventField.toString(StructuredEventField.LineupSubmissionId) -> submission.id.value,
          StructuredEventField.toString(StructuredEventField.PlayerId) -> seat.playerId.value,
          StructuredEventField.toString(StructuredEventField.Reserve) -> seat.reserve.toString
        )
      )
    }

  private def ensureNoLineupConflict(
      stage: TournamentStage,
      stageId: TournamentStageId,
      submission: StageLineupSubmission
  ): Unit =
    val submissionPlayerIds = submission.seats.map(_.playerId).distinct
    val conflictingPlayers = stage.lineupSubmissions
      .filterNot(_.clubId == submission.clubId)
      .flatMap(existing => existing.seats.map(_.playerId -> existing.clubId))
      .groupBy(_._1)
      .collect {
        case (playerId, assignments)
            if submissionPlayerIds.contains(playerId) &&
              assignments.map(_._2).distinct.nonEmpty =>
          playerId.value
      }
      .toVector

    if conflictingPlayers.nonEmpty then
      throw IllegalArgumentException(
        s"Stage ${stageId.value} already has player(s) assigned by another club: ${conflictingPlayers.mkString(", ")}"
      )

  private def resolveActiveClub(context: ApiPlanContext, clubId: ClubId): IO[ClubPrivateView] =
    ResolveClubReadModelsPrivateAPIMessage(Vector(clubId))
      .plan(context)
      .map(_.headOption.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found")))
      .flatMap { club =>
        IO.blocking(ensureClubActive(club)).as(club)
      }

  private def ensureSubmitterMatchesActor(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      submission: StageLineupSubmission
  ): IO[Unit] =
    CheckSuperAdminPrivateAPIMessage(actor).plan(context).flatMap { isSuperAdmin =>
      if !isSuperAdmin && actor.playerId.exists(_ != submission.submittedBy) then
        IO.raiseError(AuthorizationFailure("Lineup submitter must match the acting principal"))
      else IO.unit
    }

  private def ensureClubRegistered(
      tournament: Tournament,
      tournamentId: TournamentId,
      submission: StageLineupSubmission
  ): Unit =
    val isClubRegistered =
      tournament.participatingClubs.contains(submission.clubId)
    if !isClubRegistered then
      throw IllegalArgumentException(
        s"Club ${submission.clubId.value} has not accepted tournament ${tournamentId.value}"
      )

  private def ensureLineupPlayersActiveMembers(
      context: ApiPlanContext,
      club: ClubPrivateView,
      submission: StageLineupSubmission
  ): IO[Unit] =
    val playerIds = submission.seats.map(_.playerId).distinct
    for
      players <- ResolvePlayersPrivateAPIMessage(playerIds).plan(context)
      _ <- IO.blocking {
        val playersById = players.map(player => player.id -> player).toMap
        submission.seats.foreach { seat =>
          val playerId = seat.playerId
          if !club.members.contains(playerId) then
            throw IllegalArgumentException(
              s"Player ${playerId.value} is not a member of club ${submission.clubId.value}"
            )

          val player = playersById.getOrElse(playerId, throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          if player.status != PlayerStatus.Active then
            throw IllegalArgumentException(s"Player ${playerId.value} cannot be submitted to tournament lineups")
        }
      }
    yield ()

  private def ensureClubActive(club: ClubPrivateView): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubLineupCapability(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      club: ClubPrivateView
  ): IO[Unit] =
    val hasBasePermission =
      AuthCheckPermissionAPIMessage(
        operatorId = actor.playerId.map(_.value),
        permission = Permission.SubmitTournamentLineup,
        clubId = Some(club.id.value)
      ).plan(context)
    val delegatedPrivilege =
      actor.playerId
        .map(playerId => CheckClubMemberPrivilegePrivateAPIMessage(club.id, playerId, ClubPrivilegeCode.PriorityLineup).plan(context))
        .getOrElse(IO.pure(false))

    for
      hasBasePermission <- hasBasePermission
      hasDelegatedPrivilege <- delegatedPrivilege
      _ <-
        if hasBasePermission || hasDelegatedPrivilege then IO.unit
        else
          IO.raiseError(
            AuthorizationFailure(
              s"${actor.displayName} is not allowed to perform ${Permission.SubmitTournamentLineup} for club ${club.id.value}"
            )
          )
    yield ()

  private def stageLineupSubmission(
      request: SubmitStageLineupRequest,
      submittedAt: java.time.Instant
  ): StageLineupSubmission =
    StageLineupSubmission(
      id = TournamentIdGenerator.lineupSubmissionId(),
      clubId = ClubId(request.clubId),
      submittedBy = PlayerId(request.operatorId),
      submittedAt = submittedAt,
      seats = request.seats.map(stageLineupSeat),
      note = request.note
    )

  private def stageLineupSeat(request: StageLineupSeatRequest): StageLineupSeat =
    StageLineupSeat(
      playerId = PlayerId(request.playerId),
      preferredWind = request.preferredWind.map(SeatWind.valueOf),
      reserve = request.reserve
    )
