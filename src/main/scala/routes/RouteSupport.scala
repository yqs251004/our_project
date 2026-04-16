package routes

import java.time.Instant
import java.util.NoSuchElementException

import scala.util.Try

import api.OpenApiSupport
import api.contracts.JsonSupport.given
import cats.effect.IO
import database.ApplicationContext
import domain.*
import model.DomainModels.*
import objects.{ErrorResponse, PagedResponse}
import org.http4s.*
import org.typelevel.ci.CIString
import ports.*
import upickle.default.*

final class RouteSupport(
    val app: ApplicationContext,
    val storageLabel: String,
    corsAllowOrigin: String
):
  final case class PageQuery(limit: Int, offset: Int)

  private val defaultHeaders = List(
    Header.Raw(CIString("Access-Control-Allow-Origin"), corsAllowOrigin),
    Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS"),
    Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization"),
    Header.Raw(CIString("Access-Control-Max-Age"), "600")
  )

  private def withDefaultHeaders(response: Response[IO]): Response[IO] =
    response.withHeaders(Headers(response.headers.headers ++ defaultHeaders))

  def handled(io: => IO[Response[IO]]): IO[Response[IO]] =
    IO.defer(io).handleErrorWith(errorResponse)

  def errorResponse(error: Throwable): IO[Response[IO]] =
    error match
      case handled: OptimisticConcurrencyException =>
        jsonResponse(
          Status.Conflict,
          ErrorResponse(
            message = handled.getMessage,
            code = "optimistic_concurrency_conflict",
            details = Map(
              "aggregateType" -> handled.aggregateType,
              "aggregateId" -> handled.aggregateId,
              "expectedVersion" -> handled.expectedVersion.toString
            ) ++ handled.actualVersion.map(version => "actualVersion" -> version.toString)
          )
        )
      case handled: AuthorizationFailure =>
        jsonResponse(Status.Forbidden, ErrorResponse(handled.getMessage, code = "authorization_failed"))
      case handled: AuthenticationFailure =>
        jsonResponse(Status.Unauthorized, ErrorResponse(handled.getMessage, code = handled.code))
      case handled: IllegalArgumentException =>
        jsonResponse(Status.BadRequest, ErrorResponse(handled.getMessage, code = "invalid_request"))
      case handled: NoSuchElementException =>
        jsonResponse(Status.NotFound, ErrorResponse(handled.getMessage, code = "not_found"))
      case handled: ujson.ParseException =>
        jsonResponse(Status.BadRequest, ErrorResponse(s"Invalid JSON body: ${handled.getMessage}", code = "invalid_json"))
      case handled =>
        jsonResponse(
          Status.InternalServerError,
          ErrorResponse(Option(handled.getMessage).getOrElse("Internal server error"))
        )

  def textResponse(status: Status, payload: String, contentType: String): IO[Response[IO]] =
    IO.pure(
      withDefaultHeaders(
        Response[IO](status = status)
          .withEntity(payload)
          .putHeaders(Header.Raw(CIString("Content-Type"), contentType))
      )
    )

  def jsonResponse[T: Writer](status: Status, payload: T): IO[Response[IO]] =
    textResponse(status, write(payload, indent = 2), "application/json; charset=utf-8")

  def optionJsonResponse[T: Writer](value: Option[T], statusIfSome: Status = Status.Ok): IO[Response[IO]] =
    value match
      case Some(actual) => jsonResponse(statusIfSome, actual)
      case None => jsonResponse(Status.NotFound, ErrorResponse("Resource not found", code = "not_found"))

  def emptyResponse(status: Status): IO[Response[IO]] =
    IO.pure(withDefaultHeaders(Response[IO](status = status)))

  def baseUrl(request: Request[IO]): String =
    request.headers.get[headers.Host] match
      case Some(host) =>
        s"http://${host.host.toString}${host.port.map(port => s":$port").getOrElse("")}"
      case None =>
        "http://127.0.0.1"

  def openApiJson(request: Request[IO]): String =
    OpenApiSupport.openApiJson(baseUrl(request))

  def parseEnum[E](label: String, value: String)(parse: String => E): E =
    Try(parse(value)).getOrElse(throw IllegalArgumentException(s"Invalid $label: $value"))

  def containsIgnoreCase(value: String, fragment: String): Boolean =
    value.toLowerCase.contains(fragment.toLowerCase)

  def queryParam(request: Request[IO], key: String): Option[String] =
    request.params.get(key)

  def bearerToken(request: Request[IO]): Option[String] =
    request.headers.headers
      .find(_.name == CIString("Authorization"))
      .map(_.value)
      .flatMap { rawValue =>
        val prefix = "Bearer "
        Option.when(rawValue.regionMatches(true, 0, prefix, 0, prefix.length))(
          rawValue.substring(prefix.length).trim
        ).filter(_.nonEmpty)
      }

  def queryIntParam(request: Request[IO], key: String): Option[Int] =
    queryParam(request, key).filter(_.nonEmpty).map { value =>
      Try(value.toInt).getOrElse(throw IllegalArgumentException(s"Query parameter $key must be an integer"))
    }

  def queryBooleanParam(request: Request[IO], key: String): Option[Boolean] =
    queryParam(request, key).filter(_.nonEmpty).map {
      case value if value.equalsIgnoreCase("true") => true
      case value if value.equalsIgnoreCase("false") => false
      case _ => throw IllegalArgumentException(s"Query parameter $key must be true or false")
    }

  def activeFilters(request: Request[IO], keys: String*): Map[String, String] =
    keys.flatMap(key => queryParam(request, key).filter(_.nonEmpty).map(key -> _)).toMap

  def pageQuery(request: Request[IO], defaultLimit: Int = 20, maxLimit: Int = 100): PageQuery =
    val limit = queryIntParam(request, "limit").getOrElse(defaultLimit)
    val offset = queryIntParam(request, "offset").getOrElse(0)
    require(limit > 0, "Query parameter limit must be positive")
    require(offset >= 0, "Query parameter offset must be non-negative")
    PageQuery(limit = math.min(limit, maxLimit), offset = offset)

  def pagedJsonResponse[T: Writer](
      request: Request[IO],
      items: Vector[T],
      appliedFilters: Map[String, String] = Map.empty,
      defaultLimit: Int = 20,
      maxLimit: Int = 100
  ): IO[Response[IO]] =
    val query = pageQuery(request, defaultLimit, maxLimit)
    val pagedItems = items.slice(query.offset, query.offset + query.limit)
    jsonResponse(
      Status.Ok,
      PagedResponse(
        items = pagedItems,
        total = items.size,
        limit = query.limit,
        offset = query.offset,
        hasMore = query.offset + pagedItems.size < items.size,
        appliedFilters = appliedFilters
      )
    )

  def readJsonBody[T: Reader](request: Request[IO]): IO[T] =
    request.bodyText.compile.string.map { body =>
      if body.trim.isEmpty then throw IllegalArgumentException("Request body is required")
      else read[T](body)
    }

  def readOptionalJsonBody[T: Reader](request: Request[IO]): IO[Option[T]] =
    request.bodyText.compile.string.map(body => Option(body.trim).filter(_.nonEmpty).map(read[T](_)))

  def principal(playerId: PlayerId): AccessPrincipal =
    app.playerRepository
      .findById(playerId)
      .map(_.asPrincipal)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

  def queryPrincipal(request: Request[IO]): AccessPrincipal =
    val operatorId = queryParam(request, "operatorId")
      .map(PlayerId(_))
      .getOrElse(throw IllegalArgumentException("Query parameter operatorId is required"))
    principal(operatorId)

  def guestPrincipal(sessionId: GuestSessionId): AccessPrincipal =
    app.guestSessionService
      .touchActiveSession(sessionId)
      .map(AccessPrincipal.guest)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))

  def requestActor(guestSessionId: Option[GuestSessionId], operatorId: Option[PlayerId]): AccessPrincipal =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    guestSessionId.map(guestPrincipal)
      .orElse(operatorId.map(principal))
      .getOrElse(AccessPrincipal.guest())

  def requirePermission(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Unit =
    app.authorizationService.requirePermission(
      principal = principal,
      permission = permission,
      clubId = clubId,
      tournamentId = tournamentId,
      subjectPlayerId = subjectPlayerId
    )

  def resolveCurrentSessionView(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  ): CurrentSessionView =
    if operatorId.nonEmpty && guestSessionId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    operatorId.map(playerId =>
      app.playerRepository.findById(playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    ) match
      case Some(player) =>
        CurrentSessionView(
          principalKind = SessionPrincipalKind.RegisteredPlayer,
          principalId = player.id.value,
          displayName = player.nickname,
          authenticated = true,
          roles = registeredRoleFlags(player),
          player = Some(player)
        )
      case None =>
        guestSessionId.map(sessionId =>
          app.guestSessionService.touchActiveSession(sessionId)
            .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
        ) match
          case Some(session) =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Guest,
              principalId = session.id.value,
              displayName = session.displayName,
              authenticated = true,
              roles = CurrentSessionRoleFlags(
                isGuest = true,
                isRegisteredPlayer = false,
                isClubAdmin = false,
                isTournamentAdmin = false,
                isSuperAdmin = false
              ),
              guestSession = Some(session)
            )
          case None =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Anonymous,
              principalId = "anonymous",
              displayName = "Guest",
              authenticated = false,
              roles = CurrentSessionRoleFlags(
                isGuest = true,
                isRegisteredPlayer = false,
                isClubAdmin = false,
                isTournamentAdmin = false,
                isSuperAdmin = false
              )
              )

  def registeredRoleFlags(player: Player): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
    )

  def clubApplicationsOpen(club: Club): Boolean =
    club.dissolvedAt.isEmpty && club.recruitmentPolicy.applicationsOpen

  def clubApplicationPolicy(club: Club): ClubApplicationPolicyView =
    ClubApplicationPolicyView(
      applicationsOpen = clubApplicationsOpen(club),
      requirementsText =
        if clubApplicationsOpen(club) then club.recruitmentPolicy.requirementsText else None,
      expectedReviewSlaHours =
        if clubApplicationsOpen(club) then club.recruitmentPolicy.expectedReviewSlaHours else None,
      pendingApplicationCount = club.membershipApplications.count(_.isPending)
    )

  def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    actor.isSuperAdmin || actor.playerId.exists(playerId =>
      club.admins.contains(playerId) || club.hasPrivilege(playerId, ClubPrivilege.ApproveRoster)
    )

  def canManageClubTournamentParticipation(actor: AccessPrincipal, club: Club): Boolean =
    actor.isSuperAdmin ||
      app.authorizationService.can(actor, Permission.SubmitTournamentLineup, clubId = Some(club.id)) ||
      actor.playerId.exists(playerId =>
        club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
      )

  def requireClubApplicationManager(actor: AccessPrincipal, club: Club): Unit =
    if !canManageClubApplications(actor, club) then
      throw AuthorizationFailure(s"${actor.displayName} cannot manage membership applications for club ${club.id.value}")

  def ownsClubApplication(actor: AccessPrincipal, application: ClubMembershipApplication): Boolean =
    val ownedByGuest = actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")
    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(app.playerRepository.findById).exists(player =>
        application.applicantUserId.contains(player.userId)
      )
    ownedByGuest || ownedByRegisteredPlayer

  def canWithdrawClubApplication(actor: AccessPrincipal, application: ClubMembershipApplication): Boolean =
    actor.isSuperAdmin || ownsClubApplication(actor, application)

  def requireClubApplicationViewer(actor: AccessPrincipal, club: Club, application: ClubMembershipApplication): Unit =
    if !canManageClubApplications(actor, club) && !canWithdrawClubApplication(actor, application) then
      throw AuthorizationFailure(s"${actor.displayName} cannot view membership application ${application.id.value}")

  def buildClubMembershipApplicationView(
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val applicantPlayer = application.applicantUserId.flatMap(app.playerRepository.findByUserId)
    ClubMembershipApplicationView(
      applicationId = application.id,
      clubId = club.id,
      clubName = club.name,
      applicant = ClubMembershipApplicantView(
        playerId = applicantPlayer.map(_.id),
        applicantUserId = application.applicantUserId,
        displayName = application.displayName,
        playerStatus = applicantPlayer.map(_.status),
        currentRank = applicantPlayer.map(_.currentRank),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantPlayer.map(_.boundClubIds).getOrElse(Vector.empty)
      ),
      submittedAt = application.submittedAt,
      message = application.message,
      status = application.status,
      reviewedBy = application.reviewedBy,
      reviewedByDisplayName = application.reviewedBy.flatMap(playerId => app.playerRepository.findById(playerId).map(_.nickname)),
      reviewedAt = application.reviewedAt,
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = application.isPending && canManageClubApplications(actor, club),
      canWithdraw = application.isPending && canWithdrawClubApplication(actor, application)
    )

  def buildTournamentStageDirectoryEntry(stage: TournamentStage): TournamentStageDirectoryEntry =
    TournamentStageDirectoryEntry(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )

  def buildTournamentLineupSubmissionView(submission: StageLineupSubmission): TournamentLineupSubmissionView =
    buildTournamentLineupSubmissionView(
      submission,
      loadClubsById(Vector(submission.clubId)),
      loadPlayersById(Vector(submission.submittedBy))
    )

  private def buildTournamentLineupSubmissionView(
      submission: StageLineupSubmission,
      clubsById: Map[ClubId, Club],
      playersById: Map[PlayerId, Player]
  ): TournamentLineupSubmissionView =
    TournamentLineupSubmissionView(
      submissionId = submission.id,
      clubId = submission.clubId,
      clubName = clubsById.get(submission.clubId).map(_.name).getOrElse(submission.clubId.value),
      submittedBy = submission.submittedBy,
      submittedByDisplayName = playersById.get(submission.submittedBy).map(_.nickname),
      submittedAt = submission.submittedAt,
      activePlayerIds = submission.seats.filterNot(_.reserve).map(_.playerId),
      reservePlayerIds = submission.seats.filter(_.reserve).map(_.playerId),
      note = submission.note
    )

  def buildTournamentOperationsStageView(stage: TournamentStage): TournamentOperationsStageView =
    buildTournamentOperationsStageView(
      stage,
      loadClubsById(stage.lineupSubmissions.map(_.clubId)),
      loadPlayersById(stage.lineupSubmissions.map(_.submittedBy))
    )

  private def buildTournamentOperationsStageView(
      stage: TournamentStage,
      clubsById: Map[ClubId, Club],
      playersById: Map[PlayerId, Player]
  ): TournamentOperationsStageView =
    TournamentOperationsStageView(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size,
      lineupSubmissions = stage.lineupSubmissions
        .sortBy(_.submittedAt)
        .map(submission => buildTournamentLineupSubmissionView(submission, clubsById, playersById))
    )

  def buildTournamentDetailView(tournament: Tournament): TournamentDetailView =
    val tournamentClubIds = tournamentRelatedClubIds(tournament)
    val clubsById = loadClubsById(tournamentClubIds)
    val participantIds = tournamentParticipantIds(tournament, clubsById)
    val playerIdsForLookup = (
      tournament.participatingClubs.distinct.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members)) ++
        participantIds ++
        tournament.stages.flatMap(_.lineupSubmissions.map(_.submittedBy))
    ).distinct
    val playersById = loadPlayersById(playerIdsForLookup)
    val participatingClubs = tournament.participatingClubs.distinct.flatMap { clubId =>
      clubsById.get(clubId).map { club =>
        TournamentParticipantClubView(
          clubId = club.id,
          clubName = club.name,
          memberCount = club.members.size,
          activeMemberCount = club.members.count(playerId =>
            playersById.get(playerId).exists(_.status == PlayerStatus.Active)
          )
        )
      }
    }.sortBy(club => (club.clubName, club.clubId.value))

    val participatingPlayers = participantIds.flatMap { playerId =>
      playersById.get(playerId).map { player =>
        TournamentParticipantPlayerView(
          playerId = player.id,
          nickname = player.nickname,
          status = player.status,
          elo = player.elo,
          currentRank = player.currentRank,
          clubIds = player.boundClubIds
        )
      }
    }.sortBy(player => (player.nickname, player.playerId.value))

    val whitelistedClubIds = tournament.whitelist.flatMap(_.clubId).distinct.sortBy(_.value)
    val whitelistedPlayerIds = tournament.whitelist.flatMap(_.playerId).distinct.sortBy(_.value)

    TournamentDetailView(
      tournamentId = tournament.id,
      name = tournament.name,
      organizer = tournament.organizer,
      status = tournament.status,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      participatingClubs = participatingClubs,
      participatingPlayers = participatingPlayers,
      whitelistSummary = TournamentWhitelistSummaryView(
        totalEntries = tournament.whitelist.size,
        clubCount = whitelistedClubIds.size,
        playerCount = whitelistedPlayerIds.size,
        clubIds = whitelistedClubIds,
        playerIds = whitelistedPlayerIds
      ),
      stages = tournament.stages.sortBy(_.order).map(stage =>
        buildTournamentOperationsStageView(stage, clubsById, playersById)
      )
    )

  def buildTournamentDetailView(tournamentId: TournamentId): Option[TournamentDetailView] =
    app.tournamentRepository.findById(tournamentId).map(buildTournamentDetailView)

  def buildTournamentMutationView(
      tournamentId: TournamentId,
      scheduledTables: Vector[Table] = Vector.empty
  ): Option[TournamentMutationView] =
    buildTournamentDetailView(tournamentId).map(detail =>
      TournamentMutationView(
        tournament = detail,
        scheduledTables = scheduledTables.sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
      )
    )

  def buildClubTournamentParticipationView(
      clubId: ClubId,
      tournament: Tournament,
      viewer: AccessPrincipal
  ): Option[ClubTournamentParticipationView] =
    val club = app.clubRepository.findById(clubId)
    val clubVisibleToViewer =
      club.exists(currentClub => canManageClubTournamentParticipation(viewer, currentClub))
    val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(clubId))
    val isParticipating = tournament.participatingClubs.contains(clubId)
    if !isWhitelisted && !isParticipating then None
    else
      val stageName = tournament.stages
        .sortBy(_.order)
        .find(stage => stage.status != StageStatus.Completed && stage.status != StageStatus.Archived)
        .orElse(tournament.stages.sortBy(_.order).lastOption)
        .map(_.name)
      Some(
        ClubTournamentParticipationView(
          clubId = clubId,
          tournamentId = tournament.id,
          name = tournament.name,
          status = tournament.status,
          clubParticipationStatus =
            if isParticipating then ClubTournamentParticipationStatus.Participating
            else ClubTournamentParticipationStatus.Invited,
          stageName = stageName,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          canViewDetail = tournament.status != TournamentStatus.Draft || clubVisibleToViewer,
          canSubmitLineup =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Draft &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived &&
              (isWhitelisted || isParticipating),
          canDecline =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Completed &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived
        )
      )

  def buildPublicTournamentSummaryView(tournament: Tournament): PublicTournamentSummaryView =
    buildPublicTournamentSummaryView(
      tournament,
      loadClubsById(tournamentRelatedClubIds(tournament))
    )

  def buildPublicTournamentSummaryViews(tournaments: Vector[Tournament]): Vector[PublicTournamentSummaryView] =
    val clubsById = loadClubsById(tournaments.flatMap(tournamentRelatedClubIds))
    tournaments.map(tournament => buildPublicTournamentSummaryView(tournament, clubsById))

  private def buildPublicTournamentSummaryView(
      tournament: Tournament,
      clubsById: Map[ClubId, Club]
  ): PublicTournamentSummaryView =
    PublicTournamentSummaryView(
      tournamentId = tournament.id,
      name = tournament.name,
      organizer = tournament.organizer,
      status = tournament.status,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      stageCount = tournament.stages.size,
      activeStageCount = tournament.stages.count(stage =>
        stage.status == StageStatus.Active || stage.status == StageStatus.Ready
      ),
      participantCount = tournamentParticipantIds(tournament, clubsById).size,
      clubCount = tournament.participatingClubs.distinct.size,
      playerCount = tournament.participatingPlayers.distinct.size
    )

  def buildPublicTournamentDetailView(tournamentId: TournamentId): Option[PublicTournamentDetailView] =
    app.tournamentRepository.findById(tournamentId)
      .filter(_.status != TournamentStatus.Draft)
      .map { tournament =>
        val clubsById = loadClubsById(tournamentRelatedClubIds(tournament))
        val tablesByStage = app.tableRepository.findByTournamentIds(Vector(tournament.id))
          .groupBy(_.stageId)
          .withDefaultValue(Vector.empty)
        val stages = tournament.stages
          .sortBy(_.order)
          .map { stage =>
            val tables = tablesByStage(stage.id)
            val bracket =
              if stage.format == StageFormat.Knockout || stage.format == StageFormat.Finals then
                Some(app.tournamentService.stageKnockoutBracket(tournament.id, stage.id))
              else None

            PublicTournamentStageView(
              stageId = stage.id,
              name = stage.name,
              format = stage.format,
              order = stage.order,
              status = stage.status,
              currentRound = stage.currentRound,
              roundCount = stage.roundCount,
              tableCount = tables.size,
              archivedTableCount = tables.count(_.status == TableStatus.Archived),
              pendingTablePlanCount = stage.pendingTablePlans.size,
              standings = Some(app.tournamentService.stageStandings(tournament.id, stage.id)),
              bracket = bracket
            )
          }

        PublicTournamentDetailView(
          tournamentId = tournament.id,
          name = tournament.name,
          organizer = tournament.organizer,
          status = tournament.status,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          clubIds = tournament.participatingClubs.distinct,
          playerIds = tournamentParticipantIds(tournament, clubsById),
          whitelistCount = tournament.whitelist.size,
          stages = stages
        )
      }

  def tournamentParticipantIds(tournament: Tournament): Vector[PlayerId] =
    tournamentParticipantIds(
      tournament,
      loadClubsById(tournamentRelatedClubIds(tournament))
    )

  private def tournamentParticipantIds(
      tournament: Tournament,
      clubsById: Map[ClubId, Club]
  ): Vector[PlayerId] =
    val clubMembers = tournament.participatingClubs.flatMap(clubId =>
      clubsById.get(clubId).toVector.flatMap(_.members)
    )
    val whitelistedClubMembers = tournament.whitelist.flatMap(entry =>
      entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
    )

    (tournament.participatingPlayers ++ tournament.whitelist.flatMap(_.playerId) ++ clubMembers ++ whitelistedClubMembers)
      .distinct

  def buildPublicClubDetailView(clubId: ClubId): Option[PublicClubDetailView] =
    app.clubRepository.findById(clubId)
      .filter(_.dissolvedAt.isEmpty)
      .map { club =>
        val recentRecords = app.matchRecordRepository.findRecentByClub(club.id, limit = 8)
        val lineupPlayerIds = latestClubLineupPlayerIds(club).getOrElse(club.members)
        val recentTournamentIds = recentRecords.map(_.tournamentId).distinct
        val tournamentsById = app.tournamentRepository.findByIds(recentTournamentIds)
          .map(tournament => tournament.id -> tournament)
          .toMap
        val playersById = loadPlayersById(
          (club.members ++ lineupPlayerIds ++ recentRecords.flatMap(_.seatResults.map(_.playerId))).distinct
        )
        val tournamentCache = scala.collection.mutable.Map.empty[TournamentId, Option[Tournament]]
        def tournamentFor(id: TournamentId): Option[Tournament] =
          tournamentCache.getOrElseUpdate(id, tournamentsById.get(id).orElse(app.tournamentRepository.findById(id)))
        def nicknameFor(id: PlayerId): String =
          playersById.get(id).map(_.nickname).getOrElse(id.value)

        val currentLineup = lineupPlayerIds
          .flatMap(playerId => playersById.get(playerId))
          .sortBy(player => (-player.elo, player.nickname, player.id.value))
          .map { player =>
            val privilegeSnapshot = club.memberPrivilegeSnapshot(player.id)
            PublicClubLineupMemberView(
              playerId = player.id,
              nickname = player.nickname,
              elo = player.elo,
              currentRank = player.currentRank,
              status = player.status,
              isAdmin = club.admins.contains(player.id),
              internalTitle = privilegeSnapshot.flatMap(_.internalTitle),
              privileges = privilegeSnapshot.map(_.privileges).getOrElse(Vector.empty)
            )
          }

        val recentMatches = recentRecords.map { record =>
            val tournamentName = tournamentFor(record.tournamentId).map(_.name).getOrElse(record.tournamentId.value)
            val stageName = tournamentFor(record.tournamentId)
              .flatMap(_.stages.find(_.id == record.stageId))
              .map(_.name)
              .getOrElse(record.stageId.value)
            PublicClubRecentMatchView(
              matchRecordId = record.id,
              tournamentId = record.tournamentId,
              tournamentName = tournamentName,
              stageId = record.stageId,
              stageName = stageName,
              tableId = record.tableId,
              generatedAt = record.generatedAt,
              seats = record.seatResults
                .sortBy(_.placement)
                .map { result =>
                  PublicClubRecentMatchSeatView(
                    playerId = result.playerId,
                    nickname = nicknameFor(result.playerId),
                    clubId = result.clubId,
                    seat = result.seat,
                    placement = result.placement,
                    scoreDelta = result.scoreDelta,
                    finalPoints = result.finalPoints
                  )
                }
            )
          }

        PublicClubDetailView(
          clubId = club.id,
          name = club.name,
          memberCount = club.members.size,
          activeMemberCount = club.members.count(playerId =>
            playersById.get(playerId).exists(_.status == PlayerStatus.Active)
          ),
          adminCount = club.admins.size,
          powerRating = club.powerRating,
          totalPoints = club.totalPoints,
          treasuryBalance = club.treasuryBalance,
          pointPool = club.pointPool,
          relations = club.relations,
          honors = club.honors.sortBy(honor => (honor.achievedAt, honor.title)).reverse,
          applicationPolicy = clubApplicationPolicy(club),
          currentLineup = currentLineup,
          recentMatches = recentMatches
        )
      }

  def latestClubLineupPlayerIds(club: Club): Option[Vector[PlayerId]] =
    app.tournamentRepository.findByClub(club.id)
      .filter(_.status != TournamentStatus.Draft)
      .flatMap(_.stages)
      .flatMap(_.lineupSubmissions)
      .filter(_.clubId == club.id)
      .sortBy(submission => (submission.submittedAt, submission.id.value))
      .lastOption
      .map(_.activePlayerIds)

  private def tournamentRelatedClubIds(tournament: Tournament): Vector[ClubId] =
    (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct

  private def loadPlayersById(playerIds: Iterable[PlayerId]): Map[PlayerId, Player] =
    app.playerRepository.findByIds(playerIds.toVector.distinct)
      .map(player => player.id -> player)
      .toMap

  private def loadClubsById(clubIds: Iterable[ClubId]): Map[ClubId, Club] =
    app.clubRepository.findByIds(clubIds.toVector.distinct)
      .map(club => club.id -> club)
      .toMap

  def queryDemoScenarioVariant(request: Request[IO]): DemoScenarioVariant =
    queryParam(request, "variant")
      .filter(_.nonEmpty)
      .map(parseEnum("variant", _)(DemoScenarioVariant.valueOf))
      .getOrElse(DemoScenarioVariant.Basic)
