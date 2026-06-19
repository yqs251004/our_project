package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{CheckSuperAdminPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageFunctions
import riichinexus.microservices.tournament.domain.stage.model.{StageLineupSeat, StageLineupSubmission, TournamentStage}
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.club.api.`private`.{CheckClubMemberPrivilegePrivateAPIMessage, ResolveClubReadModelsPrivateAPIMessage}
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode

import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.system.realtime.objects.{RealtimeEvent, RealtimeEventType}

import riichinexus.microservices.tournament.objects.stage.table.SeatWind
import riichinexus.microservices.tournament.objects.stage.lineup.apiTypes.{StageLineupSeatRequest, SubmitStageLineupRequest}
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentMutationView

/** 提交俱乐部在赛事阶段的出场阵容。 */
final case class TournamentStageSubmitLineupAPIMessage(tournamentId: String, stageId: String, request: SubmitStageLineupRequest) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      submittedAt <- IO.realTimeInstant
      submission <- IO.blocking(stageLineupSubmission(request, submittedAt))
      command = SubmitStageLineupCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        submission = submission,
        actor = actor
      )
      savedTournament <- submitLineup(context, command)
        .map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- publishLineupSubmitted(context, command)
      _ <- RecordBulkNotificationsPrivateAPIMessage(lineupSelectedNotifications(savedTournament, command)).plan(context)
      detail <- TournamentGetAPIMessage(command.tournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def publishLineupSubmitted(
      context: ApiPlanContext,
      command: SubmitStageLineupCommand
  ): IO[Unit] =
    context.realtimeEventBus.publish(
      RealtimeEvent(
        id = command.submission.id.value,
        eventType = RealtimeEventType.TournamentChanged,
        aggregateType = "tournament",
        aggregateId = command.tournamentId.value,
        occurredAt = command.submission.submittedAt,
        sourceEventType = "TournamentLineupSubmitted",
        actorId = Some(command.submission.submittedBy.value),
        actionUrl = Some(s"/public/tournaments/${command.tournamentId.value}")
      )
    )

  private def submitLineup(
      context: ApiPlanContext,
      command: SubmitStageLineupCommand
  ): IO[Option[Tournament]] =
    loadTournament(context, command.tournamentId).flatMap {
      case Some(tournament) => submitLineupForTournament(context, tournament, command).map(Some(_))
      case None             => IO.pure(None)
    }

  private def submitLineupForTournament(
      context: ApiPlanContext,
      tournament: Tournament,
      command: SubmitStageLineupCommand
  ): IO[Tournament] =
    for
      stage <- IO.blocking(resolveWritableStage(tournament, command))
      club <- resolveActiveClub(context, command.submission.clubId)
      _ <- requireClubLineupCapability(context, command.actor, club)
      _ <- ensureSubmitterMatchesActor(context, command.actor, command.submission)
      _ <- IO.blocking(ensureClubRegistered(tournament, command))
      _ <- ensureLineupPlayersActiveMembers(context, club, command.submission)
      savedTournament <- saveLineupSubmission(context, tournament, stage, command)
    yield savedTournament

  private def loadTournament(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): IO[Option[Tournament]] =
    IO.blocking(
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(context.connection, tournamentId)
    )

  private def resolveWritableStage(
      tournament: Tournament,
      command: SubmitStageLineupCommand
  ): TournamentStage =
    val stage = requireStage(tournament, command.stageId)
    ensureNoLineupConflict(stage, command)
    stage

  private def saveLineupSubmission(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage,
      command: SubmitStageLineupCommand
  ): IO[Tournament] =
    IO.blocking {
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(
        context.connection,
        TournamentFunctions.updateStage(
          tournament,
          command.stageId,
          _ => TournamentStageFunctions.submitLineup(stage, command.submission)
        )
      )
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def lineupSelectedNotifications(
      tournament: Tournament,
      command: SubmitStageLineupCommand
  ): Vector[CreateNotificationRequest] =
    val stage = requireStage(tournament, command.stageId)
    command.submission.seats.map { seat =>
      val roleText = if seat.reserve then "候补" else "正选"
      CreateNotificationRequest(
        recipientPlayerId = seat.playerId.value,
        notificationType = NotificationType.TournamentLineupSelected,
        title = if seat.reserve then "被列入赛事候补阵容" else "被选中参加赛事",
        body = s"你被选入赛事 ${tournament.name} 的 ${stage.name}，身份为${roleText}选手。",
        severity = Some("info"),
        sourceService = "tournament",
        sourceType = "tournament-lineup",
        sourceId = command.submission.id.value,
        actionUrl = Some(s"/public/tournaments/${tournament.id.value}"),
        objects = Map(
          "tournamentId" -> tournament.id.value,
          "stageId" -> stage.id.value,
          "clubId" -> command.submission.clubId.value,
          "lineupSubmissionId" -> command.submission.id.value,
          "playerId" -> seat.playerId.value,
          "reserve" -> seat.reserve.toString
        )
      )
    }

  private def ensureNoLineupConflict(stage: TournamentStage, command: SubmitStageLineupCommand): Unit =
    val submissionPlayerIds = command.submission.seats.map(_.playerId).distinct
    val conflictingPlayers = stage.lineupSubmissions
      .filterNot(_.clubId == command.submission.clubId)
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
        s"Stage ${command.stageId.value} already has player(s) assigned by another club: ${conflictingPlayers.mkString(", ")}"
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

  private def ensureClubRegistered(tournament: Tournament, command: SubmitStageLineupCommand): Unit =
    val isClubRegistered =
      tournament.participatingClubs.contains(command.submission.clubId)
    if !isClubRegistered then
      throw IllegalArgumentException(
        s"Club ${command.submission.clubId.value} has not accepted tournament ${command.tournamentId.value}"
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

  private final case class SubmitStageLineupCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipalPrivateView
  )

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
