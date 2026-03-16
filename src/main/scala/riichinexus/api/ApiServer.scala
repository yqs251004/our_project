package riichinexus.api

import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.NoSuchElementException
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import scala.util.Try
import scala.util.Using

import riichinexus.api.ApiModels.given
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.domain.service.{AuthorizationFailure, GlobalDictionaryRegistry}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ApiServerConfig(
    host: String,
    port: Int,
    storageLabel: String
)

object ApiServerConfig:
  def fromEnv(env: collection.Map[String, String] = sys.env): ApiServerConfig =
    ApiServerConfig(
      host = env.getOrElse("RIICHI_API_HOST", "0.0.0.0"),
      port = env.get("RIICHI_API_PORT").flatMap(value => Try(value.toInt).toOption).getOrElse(8080),
      storageLabel =
        env.get("RIICHI_STORAGE").map(_.trim.toLowerCase).getOrElse {
          if env.contains("RIICHI_DB_URL") then "postgres" else "memory"
        }
    )

final class RiichiNexusApiServer(
    app: ApplicationContext,
    config: ApiServerConfig
):
  private val server = HttpServer.create(InetSocketAddress(config.host, config.port), 0)

  server.createContext("/", ApiHandler(app, config))
  server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())

  def start(): Unit =
    server.start()

  def stop(delaySeconds: Int = 0): Unit =
    server.stop(delaySeconds)

  def port: Int =
    server.getAddress.getPort

private final class ApiHandler(
    app: ApplicationContext,
    config: ApiServerConfig
) extends HttpHandler:
  private final case class PageQuery(limit: Int, offset: Int)

  override def handle(exchange: HttpExchange): Unit =
    try
      route(exchange)
    catch
      case error: AuthorizationFailure =>
        sendJson(exchange, 403, ApiError(error.getMessage))
      case error: IllegalArgumentException =>
        sendJson(exchange, 400, ApiError(error.getMessage))
      case error: NoSuchElementException =>
        sendJson(exchange, 404, ApiError(error.getMessage))
      case error: ujson.ParseException =>
        sendJson(exchange, 400, ApiError(s"Invalid JSON body: ${error.getMessage}"))
      case error: Throwable =>
        sendJson(exchange, 500, ApiError(Option(error.getMessage).getOrElse("Internal server error")))

  private def route(exchange: HttpExchange): Unit =
    val method = exchange.getRequestMethod.toUpperCase
    val path = exchange.getRequestURI.getPath
    val segments = path.split("/").toVector.filter(_.nonEmpty)

    (method, segments) match
      case ("GET", Vector()) | ("GET", Vector("health")) =>
        sendJson(
          exchange,
          200,
          HealthResponse(
            status = "ok",
            storage = config.storageLabel,
            timestamp = Instant.now()
          )
        )

      case ("GET", Vector("public", "schedules")) =>
        val tournamentStatusFilter = queryParam(exchange, "tournamentStatus").filter(_.nonEmpty).map(
          parseEnum("tournamentStatus", _)(TournamentStatus.valueOf)
        )
        val stageStatusFilter = queryParam(exchange, "stageStatus").filter(_.nonEmpty).map(
          parseEnum("stageStatus", _)(StageStatus.valueOf)
        )
        val schedules = app.publicQueryService.publicSchedules()
          .filter(schedule => tournamentStatusFilter.forall(_ == schedule.tournamentStatus))
          .filter(schedule => stageStatusFilter.forall(_ == schedule.stageStatus))
          .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))
        sendPagedJson(
          exchange,
          schedules,
          activeFilters(exchange, "tournamentStatus", "stageStatus")
        )
      case ("GET", Vector("public", "clubs")) =>
        val nameFilter = queryParam(exchange, "name").filter(_.nonEmpty)
        val relationFilter = queryParam(exchange, "relation").filter(_.nonEmpty).map(
          parseEnum("relation", _)(ClubRelationKind.valueOf)
        )
        val clubs = app.publicQueryService.publicClubDirectory()
          .filter(club => nameFilter.forall(containsIgnoreCase(club.name, _)))
          .filter(club => relationFilter.forall(relation => club.relations.exists(_.relation == relation)))
          .sortBy(_.name)
        sendPagedJson(exchange, clubs, activeFilters(exchange, "name", "relation"))
      case ("GET", Vector("public", "leaderboards", "players")) =>
        val clubIdFilter = queryParam(exchange, "clubId").filter(_.nonEmpty).map(ClubId(_))
        val statusFilter = queryParam(exchange, "status").filter(_.nonEmpty).map(
          parseEnum("status", _)(PlayerStatus.valueOf)
        )
        val leaderboard = app.publicQueryService.publicPlayerLeaderboard(app.playerRepository.findAll().size)
          .filter(entry => clubIdFilter.forall(entry.clubIds.contains))
          .filter(entry => statusFilter.forall(_ == entry.status))
        sendPagedJson(exchange, leaderboard, activeFilters(exchange, "clubId", "status"))
      case ("GET", Vector("public", "leaderboards", "clubs")) =>
        val nameFilter = queryParam(exchange, "name").filter(_.nonEmpty)
        val leaderboard = app.publicQueryService.publicClubLeaderboard(app.clubRepository.findAll().size)
          .filter(entry => nameFilter.forall(containsIgnoreCase(entry.name, _)))
        sendPagedJson(exchange, leaderboard, activeFilters(exchange, "name"))

      case ("GET", Vector("players")) =>
        val clubIdFilter = queryParam(exchange, "clubId").filter(_.nonEmpty).map(ClubId(_))
        val statusFilter = queryParam(exchange, "status").filter(_.nonEmpty).map(
          parseEnum("status", _)(PlayerStatus.valueOf)
        )
        val nicknameFilter = queryParam(exchange, "nickname").filter(_.nonEmpty)
        val players = app.playerRepository.findAll()
          .filter(player => clubIdFilter.forall(player.boundClubIds.contains))
          .filter(player => statusFilter.forall(_ == player.status))
          .filter(player => nicknameFilter.forall(containsIgnoreCase(player.nickname, _)))
          .sortBy(player => (player.nickname, player.id.value))
        sendPagedJson(exchange, players, activeFilters(exchange, "clubId", "status", "nickname"))
      case ("GET", Vector("players", playerId)) =>
        sendOption(exchange, app.playerRepository.findById(PlayerId(playerId)))
      case ("POST", Vector("players")) =>
        val request = readJsonBody[CreatePlayerRequest](exchange)
        val player = app.playerService.registerPlayer(
          userId = request.userId,
          nickname = request.nickname,
          rank = request.toRankSnapshot,
          initialElo = request.initialElo
        )
        sendJson(exchange, 201, player)

      case ("GET", Vector("guest-sessions")) =>
        val sessions = app.guestSessionRepository.findAll().sortBy(session =>
          (session.createdAt, session.id.value)
        )
        sendPagedJson(exchange, sessions, activeFilters(exchange))
      case ("POST", Vector("guest-sessions")) =>
        val request = readOptionalJsonBody[CreateGuestSessionRequest](exchange)
        sendJson(
          exchange,
          201,
          app.guestSessionService.createSession(
            displayName = request.flatMap(_.displayName).getOrElse("guest")
          )
        )
      case ("GET", Vector("guest-sessions", sessionId)) =>
        sendOption(exchange, app.guestSessionService.findSession(GuestSessionId(sessionId)))

      case ("GET", Vector("clubs")) =>
        val activeOnly = queryBooleanParam(exchange, "activeOnly")
        val memberIdFilter = queryParam(exchange, "memberId").filter(_.nonEmpty).map(PlayerId(_))
        val adminIdFilter = queryParam(exchange, "adminId").filter(_.nonEmpty).map(PlayerId(_))
        val nameFilter = queryParam(exchange, "name").filter(_.nonEmpty)
        val clubs = app.clubRepository.findAll()
          .filter(club => activeOnly.forall(flag => !flag || club.dissolvedAt.isEmpty))
          .filter(club => memberIdFilter.forall(club.members.contains))
          .filter(club => adminIdFilter.forall(club.admins.contains))
          .filter(club => nameFilter.forall(containsIgnoreCase(club.name, _)))
          .sortBy(club => (club.dissolvedAt.nonEmpty, club.name, club.id.value))
        sendPagedJson(exchange, clubs, activeFilters(exchange, "activeOnly", "memberId", "adminId", "name"))
      case ("GET", Vector("clubs", clubId)) =>
        sendOption(exchange, app.clubRepository.findById(ClubId(clubId)))
      case ("GET", Vector("clubs", clubId, "members")) =>
        val statusFilter = queryParam(exchange, "status").filter(_.nonEmpty).map(
          parseEnum("status", _)(PlayerStatus.valueOf)
        )
        val nicknameFilter = queryParam(exchange, "nickname").filter(_.nonEmpty)
        val members = app.playerRepository.findByClub(ClubId(clubId))
          .filter(player => statusFilter.forall(_ == player.status))
          .filter(player => nicknameFilter.forall(containsIgnoreCase(player.nickname, _)))
          .sortBy(player => (player.nickname, player.id.value))
        sendPagedJson(exchange, members, activeFilters(exchange, "status", "nickname"))
      case ("GET", Vector("clubs", clubId, "member-privileges")) =>
        val playerIdFilter = queryParam(exchange, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val privilegeFilter = queryParam(exchange, "privilege").filter(_.nonEmpty).map(_.trim.toLowerCase)
        val rankCodeFilter = queryParam(exchange, "rankCode").filter(_.nonEmpty).map(_.trim.toLowerCase)
        val snapshots = app.clubService.listMemberPrivilegeSnapshots(ClubId(clubId))
          .filter(snapshot => playerIdFilter.forall(_ == snapshot.playerId))
          .filter(snapshot => privilegeFilter.forall(snapshot.privileges.contains))
          .filter(snapshot => rankCodeFilter.forall(_ == snapshot.rankCode.trim.toLowerCase))
        sendPagedJson(exchange, snapshots, activeFilters(exchange, "playerId", "privilege", "rankCode"))
      case ("GET", Vector("clubs", clubId, "member-privileges", playerId)) =>
        sendOption(exchange, app.clubService.memberPrivilegeSnapshot(ClubId(clubId), PlayerId(playerId)))
      case ("GET", Vector("clubs", clubId, "applications")) =>
        val applications = app.clubRepository
          .findById(ClubId(clubId))
          .map(_.membershipApplications
            .filter(application =>
              queryParam(exchange, "status")
                .filter(_.nonEmpty)
                .map(parseEnum("status", _)(ClubMembershipApplicationStatus.valueOf))
                .forall(_ == application.status)
            )
            .filter(application =>
              queryParam(exchange, "applicantUserId")
                .filter(_.nonEmpty)
                .forall(value => application.applicantUserId.contains(value))
            )
            .filter(application =>
              queryParam(exchange, "displayName")
                .filter(_.nonEmpty)
                .forall(containsIgnoreCase(application.displayName, _))
            )
            .sortBy(_.submittedAt)
          )
          .getOrElse(throw NoSuchElementException(s"Club $clubId was not found"))
        sendPagedJson(
          exchange,
          applications,
          activeFilters(exchange, "status", "applicantUserId", "displayName")
        )
      case ("POST", Vector("clubs")) =>
        val request = readJsonBody[CreateClubRequest](exchange)
        val club = app.clubService.createClub(
          name = request.name,
          creatorId = request.creator,
          actor = principal(request.creator)
        )
        sendJson(exchange, 201, club)
      case ("POST", Vector("clubs", clubId, "members")) =>
        val request = readJsonBody[AddClubMemberRequest](exchange)
        sendOption(
          exchange,
          app.clubService.addMember(
            ClubId(clubId),
            request.player,
            request.operator.map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("clubs", clubId, "members", playerId, "remove")) =>
        val request = readJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.clubService.removeMember(
            ClubId(clubId),
            PlayerId(playerId),
            request.operator.map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("clubs", clubId, "applications")) =>
        val request = readJsonBody[ClubMembershipApplicationRequest](exchange)
        val actor = requestActor(request.session, request.operator)
        val applicantUserId =
          request.applicantUserId
            .orElse(request.session.map(session => s"guest:${session.value}"))
            .orElse(request.operator.flatMap(playerId => app.playerRepository.findById(playerId).map(_.userId)))
        val displayName =
          request.session.map(_ => actor.displayName)
            .orElse(request.operator.flatMap(playerId => app.playerRepository.findById(playerId).map(_.nickname)))
            .getOrElse(request.displayName)
        sendOption(
          exchange,
          app.clubService.applyForMembership(
            clubId = ClubId(clubId),
            applicantUserId = applicantUserId,
            displayName = displayName,
            message = request.message,
            actor = actor
          )
        )
      case ("POST", Vector("clubs", clubId, "applications", applicationId, "withdraw")) =>
        val request = readJsonBody[WithdrawClubApplicationRequest](exchange)
        sendOption(
          exchange,
          app.clubService.withdrawMembershipApplication(
            clubId = ClubId(clubId),
            applicationId = MembershipApplicationId(applicationId),
            actor = requestActor(request.session, request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "applications", applicationId, "approve")) =>
        val request = readJsonBody[ApproveClubApplicationRequest](exchange)
        sendOption(
          exchange,
          app.clubService.approveMembershipApplication(
            clubId = ClubId(clubId),
            applicationId = MembershipApplicationId(applicationId),
            playerId = request.player,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "applications", applicationId, "reject")) =>
        val request = readJsonBody[RejectClubApplicationRequest](exchange)
        sendOption(
          exchange,
          app.clubService.rejectMembershipApplication(
            clubId = ClubId(clubId),
            applicationId = MembershipApplicationId(applicationId),
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "admins")) =>
        val request = readJsonBody[AssignClubAdminRequest](exchange)
        sendOption(
          exchange,
          app.clubService.assignAdmin(
            clubId = ClubId(clubId),
            playerId = request.player,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("clubs", clubId, "admins", playerId, "revoke")) =>
        val request = readJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.clubService.revokeAdmin(
            clubId = ClubId(clubId),
            playerId = PlayerId(playerId),
            actor = request.operator.map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("clubs", clubId, "titles")) =>
        val request = readJsonBody[AssignClubTitleRequest](exchange)
        sendOption(
          exchange,
          app.clubService.setInternalTitle(
            clubId = ClubId(clubId),
            playerId = request.player,
            title = request.title,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "titles", playerId, "clear")) =>
        val request = readJsonBody[ClearClubTitleRequest](exchange)
        sendOption(
          exchange,
          app.clubService.clearInternalTitle(
            clubId = ClubId(clubId),
            playerId = PlayerId(playerId),
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "treasury")) =>
        val request = readJsonBody[AdjustClubTreasuryRequest](exchange)
        sendOption(
          exchange,
          app.clubService.adjustTreasury(
            clubId = ClubId(clubId),
            delta = request.delta,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "point-pool")) =>
        val request = readJsonBody[AdjustClubPointPoolRequest](exchange)
        sendOption(
          exchange,
          app.clubService.adjustPointPool(
            clubId = ClubId(clubId),
            delta = request.delta,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "member-contributions")) =>
        val request = readJsonBody[AdjustClubMemberContributionRequest](exchange)
        sendOption(
          exchange,
          app.clubService.adjustMemberContribution(
            clubId = ClubId(clubId),
            playerId = request.player,
            delta = request.delta,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "rank-tree")) =>
        val request = readJsonBody[UpdateClubRankTreeRequest](exchange)
        sendOption(
          exchange,
          app.clubService.updateRankTree(
            clubId = ClubId(clubId),
            rankTree = request.nodes,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "honors")) =>
        val request = readJsonBody[AwardClubHonorRequest](exchange)
        sendOption(
          exchange,
          app.clubService.awardHonor(
            clubId = ClubId(clubId),
            honor = request.honor,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("clubs", clubId, "honors", "revoke")) =>
        val request = readJsonBody[RevokeClubHonorRequest](exchange)
        sendOption(
          exchange,
          app.clubService.revokeHonor(
            clubId = ClubId(clubId),
            title = request.title,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("clubs", clubId, "relations")) =>
        val request = readJsonBody[UpdateClubRelationRequest](exchange)
        sendOption(
          exchange,
          app.clubService.updateRelation(
            clubId = ClubId(clubId),
            relation = request.toRelation(),
            actor = principal(request.operator)
          )
        )

      case ("GET", Vector("tournaments")) =>
        val statusFilter = queryParam(exchange, "status").filter(_.nonEmpty).map(
          parseEnum("status", _)(TournamentStatus.valueOf)
        )
        val adminIdFilter = queryParam(exchange, "adminId").filter(_.nonEmpty).map(PlayerId(_))
        val organizerFilter = queryParam(exchange, "organizer").filter(_.nonEmpty)
        val tournaments = app.tournamentRepository.findAll()
          .filter(tournament => statusFilter.forall(_ == tournament.status))
          .filter(tournament => adminIdFilter.forall(tournament.admins.contains))
          .filter(tournament => organizerFilter.forall(containsIgnoreCase(tournament.organizer, _)))
          .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))
        sendPagedJson(exchange, tournaments, activeFilters(exchange, "status", "adminId", "organizer"))
      case ("GET", Vector("tournaments", tournamentId)) =>
        sendOption(exchange, app.tournamentRepository.findById(TournamentId(tournamentId)))
      case ("GET", Vector("tournaments", tournamentId, "whitelist")) =>
        val whitelist = app.tournamentRepository
          .findById(TournamentId(tournamentId))
          .map(_.whitelist
            .filter(entry =>
              queryParam(exchange, "participantKind")
                .filter(_.nonEmpty)
                .map(parseEnum("participantKind", _)(TournamentParticipantKind.valueOf))
                .forall(_ == entry.participantKind)
            )
            .filter(entry =>
              queryParam(exchange, "playerId")
                .filter(_.nonEmpty)
                .forall(value => entry.playerId.contains(PlayerId(value)))
            )
            .filter(entry =>
              queryParam(exchange, "clubId")
                .filter(_.nonEmpty)
                .forall(value => entry.clubId.contains(ClubId(value)))
            )
          )
          .getOrElse(throw NoSuchElementException(s"Tournament $tournamentId was not found"))
        sendPagedJson(
          exchange,
          whitelist,
          activeFilters(exchange, "participantKind", "playerId", "clubId")
        )
      case ("GET", Vector("tournaments", tournamentId, "settlements")) =>
        val settlements = app.tournamentSettlementRepository.findByTournament(TournamentId(tournamentId))
          .filter(snapshot =>
            queryParam(exchange, "stageId")
              .filter(_.nonEmpty)
              .forall(value => snapshot.stageId == TournamentStageId(value))
          )
          .filter(snapshot =>
            queryParam(exchange, "status")
              .filter(_.nonEmpty)
              .forall(value => snapshot.status == TournamentSettlementStatus.valueOf(value))
          )
          .filter(snapshot =>
            queryParam(exchange, "championId")
              .filter(_.nonEmpty)
              .forall(value => snapshot.championId == PlayerId(value))
          )
          .sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))
        sendPagedJson(
          exchange,
          settlements,
          activeFilters(exchange, "stageId", "status", "championId")
        )
      case ("GET", Vector("tournaments", tournamentId, "settlements", stageId)) =>
        sendOption(
          exchange,
          app.tournamentSettlementRepository.findByTournamentAndStage(
            TournamentId(tournamentId),
            TournamentStageId(stageId)
          )
        )
      case ("POST", Vector("tournaments")) =>
        val request = readJsonBody[CreateTournamentRequest](exchange)
        val tournament = app.tournamentService.createTournament(
          name = request.name,
          organizer = request.organizer,
          startsAt = request.startsAt,
          endsAt = request.endsAt,
          stages = request.toStages,
          adminId = request.admin
        )
        sendJson(exchange, 201, tournament)
      case ("POST", Vector("tournaments", tournamentId, "publish")) =>
        val request = readOptionalJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.publishTournament(
            TournamentId(tournamentId),
            request.flatMap(_.operator).map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "start")) =>
        val request = readOptionalJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.startTournament(
            TournamentId(tournamentId),
            request.flatMap(_.operator).map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "settle")) =>
        val request = readJsonBody[SettleTournamentRequest](exchange)
        sendJson(
          exchange,
          200,
          app.tournamentService.settleTournament(
            tournamentId = TournamentId(tournamentId),
            finalStageId = request.stageId,
            prizePool = request.prizePool,
            payoutRatios = request.payoutRatios,
            houseFeeAmount = request.houseFeeAmount,
            clubShareRatio = request.clubShareRatio,
            adjustments = request.adjustments.map(_.adjustment),
            finalize = request.finalizeSettlement,
            note = request.note,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "settlements", settlementId, "finalize")) =>
        val request = readJsonBody[FinalizeTournamentSettlementRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.finalizeTournamentSettlement(
            tournamentId = TournamentId(tournamentId),
            settlementId = SettlementSnapshotId(settlementId),
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "players", playerId)) =>
        val request = readOptionalJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.registerPlayer(
            TournamentId(tournamentId),
            PlayerId(playerId),
            request.flatMap(_.operator).map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "clubs", clubId)) =>
        val request = readOptionalJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.registerClub(
            TournamentId(tournamentId),
            ClubId(clubId),
            request.flatMap(_.operator).map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "whitelist", "players", playerId)) =>
        val request = readJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.whitelistPlayer(
            TournamentId(tournamentId),
            PlayerId(playerId),
            request.operator.map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "whitelist", "clubs", clubId)) =>
        val request = readJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.whitelistClub(
            TournamentId(tournamentId),
            ClubId(clubId),
            request.operator.map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "admins")) =>
        val request = readJsonBody[AssignTournamentAdminRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.assignTournamentAdmin(
            tournamentId = TournamentId(tournamentId),
            playerId = request.player,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "admins", playerId, "revoke")) =>
        val request = readJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.revokeTournamentAdmin(
            tournamentId = TournamentId(tournamentId),
            playerId = PlayerId(playerId),
            actor = request.operator.map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "stages")) =>
        val request = readJsonBody[CreateTournamentStageRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.addStage(
            tournamentId = TournamentId(tournamentId),
            stage = request.toStage,
            actor = request.operator.map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "stages", stageId, "rules")) =>
        val request = readJsonBody[ConfigureStageRulesRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.configureStageRules(
            tournamentId = TournamentId(tournamentId),
            stageId = TournamentStageId(stageId),
            advancementRule = request.advancementRule,
            swissRule = request.swissRule,
            knockoutRule = request.knockoutRule,
            schedulingPoolSize = request.schedulingPoolSize,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "stages", stageId, "lineups")) =>
        val request = readJsonBody[SubmitStageLineupRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.submitLineup(
            tournamentId = TournamentId(tournamentId),
            stageId = TournamentStageId(stageId),
            submission = request.toSubmission,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "stages", stageId, "schedule")) =>
        val request = readOptionalJsonBody[OperatorRequest](exchange)
        sendJson(
          exchange,
          200,
          app.tournamentService.scheduleStageTables(
            TournamentId(tournamentId),
            TournamentStageId(stageId),
            request.flatMap(_.operator).map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("GET", Vector("tournaments", tournamentId, "stages", stageId, "standings")) =>
        sendJson(
          exchange,
          200,
          app.tournamentService.stageStandings(
            TournamentId(tournamentId),
            TournamentStageId(stageId)
          )
        )
      case ("GET", Vector("tournaments", tournamentId, "stages", stageId, "tables")) =>
        val statusFilter = queryParam(exchange, "status").filter(_.nonEmpty).map(
          parseEnum("status", _)(TableStatus.valueOf)
        )
        val roundNumberFilter = queryIntParam(exchange, "roundNumber")
        val playerIdFilter = queryParam(exchange, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tables = app.tableRepository.findByTournamentAndStage(
          TournamentId(tournamentId),
          TournamentStageId(stageId)
        )
          .filter(table => statusFilter.forall(_ == table.status))
          .filter(table => roundNumberFilter.forall(_ == table.stageRoundNumber))
          .filter(table => playerIdFilter.forall(playerId => table.seats.exists(_.playerId == playerId)))
          .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
        sendPagedJson(exchange, tables, activeFilters(exchange, "status", "roundNumber", "playerId"))
      case ("GET", Vector("tournaments", tournamentId, "stages", stageId, "advancement")) =>
        sendJson(
          exchange,
          200,
          app.tournamentService.stageAdvancementPreview(
            TournamentId(tournamentId),
            TournamentStageId(stageId)
          )
        )
      case ("GET", Vector("tournaments", tournamentId, "stages", stageId, "bracket")) =>
        sendJson(
          exchange,
          200,
          app.tournamentService.stageKnockoutBracket(
            TournamentId(tournamentId),
            TournamentStageId(stageId)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "stages", stageId, "advance")) =>
        val request = readJsonBody[AdvanceKnockoutStageRequest](exchange)
        sendJson(
          exchange,
          200,
          app.tournamentService.advanceKnockoutStage(
            tournamentId = TournamentId(tournamentId),
            stageId = TournamentStageId(stageId),
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("tournaments", tournamentId, "stages", stageId, "complete")) =>
        val request = readJsonBody[CompleteStageRequest](exchange)
        sendOption(
          exchange,
          app.tournamentService.completeStage(
            tournamentId = TournamentId(tournamentId),
            stageId = TournamentStageId(stageId),
            actor = principal(request.operator)
          )
        )

      case ("GET", Vector("tables")) =>
        val statusFilter = queryParam(exchange, "status").filter(_.nonEmpty).map(
          parseEnum("status", _)(TableStatus.valueOf)
        )
        val tournamentIdFilter = queryParam(exchange, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = queryParam(exchange, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val roundNumberFilter = queryIntParam(exchange, "roundNumber")
        val playerIdFilter = queryParam(exchange, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tables = app.tableRepository.findAll()
          .filter(table => statusFilter.forall(_ == table.status))
          .filter(table => tournamentIdFilter.forall(_ == table.tournamentId))
          .filter(table => stageIdFilter.forall(_ == table.stageId))
          .filter(table => roundNumberFilter.forall(_ == table.stageRoundNumber))
          .filter(table => playerIdFilter.forall(playerId => table.seats.exists(_.playerId == playerId)))
          .sortBy(table => (table.tournamentId.value, table.stageId.value, table.stageRoundNumber, table.tableNo, table.id.value))
        sendPagedJson(
          exchange,
          tables,
          activeFilters(exchange, "status", "tournamentId", "stageId", "roundNumber", "playerId")
        )
      case ("GET", Vector("tables", tableId)) =>
        sendOption(exchange, app.tableRepository.findById(TableId(tableId)))
      case ("POST", Vector("tables", tableId, "seats", seat, "state")) =>
        val request = readJsonBody[UpdateTableSeatStateRequest](exchange)
        sendOption(
          exchange,
          app.tableService.updateSeatState(
            tableId = TableId(tableId),
            seat = parseEnum("seat", seat)(SeatWind.valueOf),
            actor = principal(request.operator),
            ready = request.ready,
            disconnected = request.disconnected,
            note = request.note
          )
        )
      case ("POST", Vector("tables", tableId, "start")) =>
        val request = readOptionalJsonBody[OperatorRequest](exchange)
        sendOption(
          exchange,
          app.tableService.startTable(
            TableId(tableId),
            actor = request.flatMap(_.operator).map(principal).getOrElse(AccessPrincipal.system)
          )
        )
      case ("POST", Vector("tables", tableId, "paifu")) =>
        val request = readJsonBody[UploadPaifuRequest](exchange)
        sendOption(
          exchange,
          app.tableService.recordCompletedTable(
            tableId = TableId(tableId),
            paifu = request.paifu,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("tables", tableId, "reset")) =>
        val request = readJsonBody[ForceResetTableRequest](exchange)
        sendOption(
          exchange,
          app.tableService.forceReset(
            tableId = TableId(tableId),
            note = request.note,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("tables", tableId, "appeals")) =>
        val request = readJsonBody[FileAppealRequest](exchange)
        sendOption(
          exchange,
          app.appealService.fileAppeal(
            tableId = TableId(tableId),
            openedBy = request.player,
            description = request.description,
            attachments = request.attachments.map(_.toAttachment),
            priority = request.priorityLevel,
            dueAt = request.dueAtInstant,
            actor = principal(request.player)
          )
        )

      case ("GET", Vector("records")) =>
        val playerIdFilter = queryParam(exchange, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tournamentIdFilter = queryParam(exchange, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = queryParam(exchange, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val tableIdFilter = queryParam(exchange, "tableId").filter(_.nonEmpty).map(TableId(_))
        val records = app.matchRecordRepository.findAll()
          .filter(record => playerIdFilter.forall(record.playerIds.contains))
          .filter(record => tournamentIdFilter.forall(_ == record.tournamentId))
          .filter(record => stageIdFilter.forall(_ == record.stageId))
          .filter(record => tableIdFilter.forall(_ == record.tableId))
          .sortBy(record => (record.generatedAt, record.id.value))
        sendPagedJson(
          exchange,
          records,
          activeFilters(exchange, "playerId", "tournamentId", "stageId", "tableId")
        )
      case ("GET", Vector("records", recordId)) =>
        sendOption(exchange, app.matchRecordRepository.findById(MatchRecordId(recordId)))
      case ("GET", Vector("records", "table", tableId)) =>
        sendOption(exchange, app.matchRecordRepository.findByTable(TableId(tableId)))
      case ("GET", Vector("paifus")) =>
        val playerIdFilter = queryParam(exchange, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tournamentIdFilter = queryParam(exchange, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = queryParam(exchange, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val tableIdFilter = queryParam(exchange, "tableId").filter(_.nonEmpty).map(TableId(_))
        val paifus = app.paifuRepository.findAll()
          .filter(paifu => playerIdFilter.forall(paifu.playerIds.contains))
          .filter(paifu => tournamentIdFilter.forall(_ == paifu.metadata.tournamentId))
          .filter(paifu => stageIdFilter.forall(_ == paifu.metadata.stageId))
          .filter(paifu => tableIdFilter.forall(_ == paifu.metadata.tableId))
          .sortBy(paifu => (paifu.metadata.recordedAt, paifu.id.value))
        sendPagedJson(
          exchange,
          paifus,
          activeFilters(exchange, "playerId", "tournamentId", "stageId", "tableId")
        )
      case ("GET", Vector("paifus", paifuId)) =>
        sendOption(exchange, app.paifuRepository.findById(PaifuId(paifuId)))
      case ("GET", Vector("appeals")) =>
        val statusFilter = queryParam(exchange, "status").filter(_.nonEmpty).map(
          parseEnum("status", _)(AppealStatus.valueOf)
        )
        val priorityFilter = queryParam(exchange, "priority").filter(_.nonEmpty).map(
          parseEnum("priority", _)(AppealPriority.valueOf)
        )
        val tournamentIdFilter = queryParam(exchange, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = queryParam(exchange, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val tableIdFilter = queryParam(exchange, "tableId").filter(_.nonEmpty).map(TableId(_))
        val openedByFilter = queryParam(exchange, "openedBy").filter(_.nonEmpty).map(PlayerId(_))
        val assigneeIdFilter = queryParam(exchange, "assigneeId").filter(_.nonEmpty).map(PlayerId(_))
        val overdueOnly = queryParam(exchange, "overdueOnly").exists(_.equalsIgnoreCase("true"))
        val dueBeforeFilter = queryParam(exchange, "dueBefore").filter(_.nonEmpty).map(Instant.parse)
        val dueAfterFilter = queryParam(exchange, "dueAfter").filter(_.nonEmpty).map(Instant.parse)
        val asOf = queryParam(exchange, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val appeals = app.appealTicketRepository.findAll()
          .filter(ticket => statusFilter.forall(_ == ticket.status))
          .filter(ticket => priorityFilter.forall(_ == ticket.priority))
          .filter(ticket => tournamentIdFilter.forall(_ == ticket.tournamentId))
          .filter(ticket => stageIdFilter.forall(_ == ticket.stageId))
          .filter(ticket => tableIdFilter.forall(_ == ticket.tableId))
          .filter(ticket => openedByFilter.forall(_ == ticket.openedBy))
          .filter(ticket => assigneeIdFilter.forall(ticket.assigneeId.contains))
          .filter(ticket => !overdueOnly || ticket.dueAt.exists(_.isBefore(asOf)))
          .filter(ticket => dueBeforeFilter.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isAfter(limit))))
          .filter(ticket => dueAfterFilter.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isBefore(limit))))
          .sortBy(ticket => (ticket.updatedAt, ticket.id.value))
        sendPagedJson(
          exchange,
          appeals,
          activeFilters(exchange, "status", "priority", "tournamentId", "stageId", "tableId", "openedBy", "assigneeId", "overdueOnly", "dueBefore", "dueAfter", "asOf")
        )
      case ("GET", Vector("appeals", appealId)) =>
        sendOption(exchange, app.appealTicketRepository.findById(AppealTicketId(appealId)))
      case ("GET", Vector("audits")) =>
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(
          operator,
          Permission.ViewAuditTrail
        )
        val aggregateTypeFilter = queryParam(exchange, "aggregateType").filter(_.nonEmpty)
        val aggregateIdFilter = queryParam(exchange, "aggregateId").filter(_.nonEmpty)
        val actorIdFilter = queryParam(exchange, "actorId").filter(_.nonEmpty).map(PlayerId(_))
        val eventTypeFilter = queryParam(exchange, "eventType").filter(_.nonEmpty)
        val audits = app.auditEventRepository.findAll()
          .filter(entry => aggregateTypeFilter.forall(_ == entry.aggregateType))
          .filter(entry => aggregateIdFilter.forall(_ == entry.aggregateId))
          .filter(entry => actorIdFilter.forall(entry.actorId.contains))
          .filter(entry => eventTypeFilter.forall(_ == entry.eventType))
          .sortBy(entry => (entry.occurredAt, entry.id.value))
        sendPagedJson(
          exchange,
          audits,
          activeFilters(exchange, "aggregateType", "aggregateId", "actorId", "eventType", "operatorId")
        )
      case ("GET", Vector("audits", aggregateType, aggregateId)) =>
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(
          operator,
          Permission.ViewAuditTrail
        )
        val actorIdFilter = queryParam(exchange, "actorId").filter(_.nonEmpty).map(PlayerId(_))
        val eventTypeFilter = queryParam(exchange, "eventType").filter(_.nonEmpty)
        val audits = app.auditEventRepository.findByAggregate(aggregateType, aggregateId)
          .filter(entry => actorIdFilter.forall(entry.actorId.contains))
          .filter(entry => eventTypeFilter.forall(_ == entry.eventType))
          .sortBy(entry => (entry.occurredAt, entry.id.value))
        sendPagedJson(
          exchange,
          audits,
          activeFilters(exchange, "actorId", "eventType", "operatorId") ++
            Map("aggregateType" -> aggregateType, "aggregateId" -> aggregateId)
        )
      case ("POST", Vector("appeals", appealId, "resolve")) =>
        val request = readJsonBody[ResolveAppealRequest](exchange)
        sendOption(
          exchange,
          app.appealService.resolveAppeal(
            ticketId = AppealTicketId(appealId),
            verdict = request.verdict,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("appeals", appealId, "adjudicate")) =>
        val request = readJsonBody[AdjudicateAppealRequest](exchange)
        sendOption(
          exchange,
          app.appealService.adjudicateAppeal(
            ticketId = AppealTicketId(appealId),
            decision = request.decisionType,
            verdict = request.verdict,
            actor = principal(request.operator),
            tableResolution = request.resolution,
            note = request.note
          )
        )
      case ("POST", Vector("appeals", appealId, "workflow")) =>
        val request = readJsonBody[UpdateAppealWorkflowRequest](exchange)
        sendOption(
          exchange,
          app.appealService.updateAppealWorkflow(
            ticketId = AppealTicketId(appealId),
            actor = principal(request.operator),
            assigneeId = request.assignee,
            clearAssignee = request.clearAssignee,
            priority = request.priorityLevel,
            dueAt = request.dueAtInstant,
            clearDueAt = request.clearDueAt,
            note = request.note
          )
        )
      case ("POST", Vector("appeals", appealId, "reopen")) =>
        val request = readJsonBody[ReopenAppealRequest](exchange)
        sendOption(
          exchange,
          app.appealService.reopenAppeal(
            ticketId = AppealTicketId(appealId),
            reason = request.reason,
            actor = principal(request.operator),
            note = request.note
          )
        )

      case ("GET", Vector("dashboards", "players", playerId)) =>
        val targetPlayerId = PlayerId(playerId)
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(
          operator,
          Permission.ViewOwnDashboard,
          subjectPlayerId = Some(targetPlayerId)
        )
        sendOption(exchange, app.dashboardRepository.findByOwner(DashboardOwner.Player(targetPlayerId)))
      case ("GET", Vector("dashboards", "clubs", clubId)) =>
        val targetClubId = ClubId(clubId)
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(
          operator,
          Permission.ViewClubDashboard,
          clubId = Some(targetClubId)
        )
        sendOption(exchange, app.dashboardRepository.findByOwner(DashboardOwner.Club(targetClubId)))
      case ("GET", Vector("advanced-stats", "players", playerId)) =>
        val targetPlayerId = PlayerId(playerId)
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(
          operator,
          Permission.ViewOwnDashboard,
          subjectPlayerId = Some(targetPlayerId)
        )
        sendOption(exchange, app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(targetPlayerId)))
      case ("GET", Vector("advanced-stats", "clubs", clubId)) =>
        val targetClubId = ClubId(clubId)
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(
          operator,
          Permission.ViewClubDashboard,
          clubId = Some(targetClubId)
        )
        sendOption(exchange, app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(targetClubId)))
      case ("GET", Vector("admin", "advanced-stats", "tasks")) =>
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(operator, Permission.ManageGlobalDictionary)
        val statusFilter = queryParam(exchange, "status").map(AdvancedStatsRecomputeTaskStatus.valueOf)
        val tasks = app.advancedStatsRecomputeTaskRepository.findAll()
          .filter(task => statusFilter.forall(_ == task.status))
        sendPagedJson(exchange, tasks, activeFilters(exchange, "status"))
      case ("GET", Vector("admin", "advanced-stats", "summary")) =>
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = queryParam(exchange, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        sendJson(exchange, 200, app.advancedStatsPipelineService.taskQueueSummary(asOf))
      case ("GET", Vector("admin", "event-cascade-records")) =>
        val operator = queryPrincipal(exchange)
        app.authorizationService.requirePermission(operator, Permission.ManageGlobalDictionary)
        val statusFilter = queryParam(exchange, "status").map(EventCascadeStatus.valueOf)
        val consumerFilter = queryParam(exchange, "consumer").map(EventCascadeConsumer.valueOf)
        val eventTypeFilter = queryParam(exchange, "eventType").filter(_.nonEmpty)
        val aggregateTypeFilter = queryParam(exchange, "aggregateType").filter(_.nonEmpty)
        val aggregateIdFilter = queryParam(exchange, "aggregateId").filter(_.nonEmpty)
        val records = app.eventCascadeRecordRepository.findAll()
          .filter(record => statusFilter.forall(_ == record.status))
          .filter(record => consumerFilter.forall(_ == record.consumer))
          .filter(record => eventTypeFilter.forall(_ == record.eventType))
          .filter(record => aggregateTypeFilter.forall(_ == record.aggregateType))
          .filter(record => aggregateIdFilter.forall(_ == record.aggregateId))
          .sortBy(record => (record.occurredAt, record.id.value))
        sendPagedJson(exchange, records, activeFilters(exchange, "status", "consumer", "eventType", "aggregateType", "aggregateId"))

      case ("GET", Vector("dictionary")) =>
        val prefixFilter = queryParam(exchange, "prefix").filter(_.nonEmpty)
        val updatedByFilter = queryParam(exchange, "updatedBy").filter(_.nonEmpty).map(PlayerId(_))
        val entries = app.globalDictionaryRepository.findAll()
          .filter(entry => prefixFilter.forall(prefix => entry.key.startsWith(prefix)))
          .filter(entry => updatedByFilter.forall(_ == entry.updatedBy))
          .sortBy(_.key)
        sendPagedJson(exchange, entries, activeFilters(exchange, "prefix", "updatedBy"))
      case ("GET", Vector("dictionary", "schema")) =>
        sendJson(exchange, 200, GlobalDictionaryRegistry.schemaView)
      case ("GET", Vector("dictionary", "namespaces", "backlog")) =>
        val operator = queryPrincipal(exchange)
        val asOf = queryParam(exchange, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val dueSoonHours = queryParam(exchange, "dueSoonHours").filter(_.nonEmpty).map(_.toLong).getOrElse(24L)
        sendJson(
          exchange,
          200,
          app.superAdminService.dictionaryNamespaceBacklog(
            actor = operator,
            asOf = asOf,
            dueSoonWindow = java.time.Duration.ofHours(dueSoonHours)
          )
        )
      case ("GET", Vector("dictionary", "namespaces")) =>
        val operator = queryPrincipal(exchange)
        val statusFilter = queryParam(exchange, "status").map(DictionaryNamespaceReviewStatus.valueOf)
        val contextClubFilter = queryParam(exchange, "contextClubId").filter(_.nonEmpty).map(ClubId(_))
        val ownerFilter = queryParam(exchange, "ownerId").filter(_.nonEmpty).map(PlayerId(_))
        val requestedByFilter = queryParam(exchange, "requestedBy").filter(_.nonEmpty).map(PlayerId(_))
        val reviewedByFilter = queryParam(exchange, "reviewedBy").filter(_.nonEmpty).map(PlayerId(_))
        val asOf = queryParam(exchange, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val overdueOnly = queryParam(exchange, "overdueOnly").exists(_.equalsIgnoreCase("true"))
        val dueBefore = queryParam(exchange, "dueBefore").filter(_.nonEmpty).map(Instant.parse)
        val dueAfter = queryParam(exchange, "dueAfter").filter(_.nonEmpty).map(Instant.parse)
        val namespaces = app.dictionaryNamespaceRepository.findAll()
          .filter(registration => statusFilter.forall(_ == registration.status))
          .filter(registration => contextClubFilter.forall(clubId => registration.contextClubId.contains(clubId)))
          .filter(registration => ownerFilter.forall(_ == registration.ownerPlayerId))
          .filter(registration => requestedByFilter.forall(_ == registration.requestedBy))
          .filter(registration => reviewedByFilter.forall(reviewer => registration.reviewedBy.contains(reviewer)))
          .filter(registration => !overdueOnly || registration.isPendingOverdue(asOf))
          .filter(registration => dueBefore.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isAfter(bound))))
          .filter(registration => dueAfter.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isBefore(bound))))
          .filter(registration =>
            operator.isSuperAdmin ||
              operator.playerId.exists(registration.hasWriteAccess) ||
              operator.playerId.contains(registration.requestedBy)
          )
        sendPagedJson(exchange, namespaces, activeFilters(exchange, "status", "contextClubId", "ownerId", "requestedBy", "reviewedBy", "asOf", "overdueOnly", "dueBefore", "dueAfter"))
      case ("POST", Vector("dictionary", "namespaces")) =>
        val request = readJsonBody[RequestDictionaryNamespaceRequest](exchange)
        sendJson(
          exchange,
          201,
          app.superAdminService.requestDictionaryNamespace(
            namespacePrefix = request.namespacePrefix,
            actor = principal(request.operator),
            contextClubId = request.contextClub,
            ownerPlayerId = request.owner,
            coOwnerPlayerIds = request.coOwners,
            editorPlayerIds = request.editors,
            note = request.note,
            reviewDueAt = request.parsedReviewDueAt
          )
        )
      case ("POST", Vector("dictionary", "namespaces", "review")) =>
        val request = readJsonBody[ReviewDictionaryNamespaceRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.reviewDictionaryNamespace(
            namespacePrefix = request.namespacePrefix,
            approve = request.approve,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("dictionary", "namespaces", "transfer")) =>
        val request = readJsonBody[TransferDictionaryNamespaceRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.transferDictionaryNamespace(
            namespacePrefix = request.namespacePrefix,
            newOwnerId = request.newOwner,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("dictionary", "namespaces", "collaborators")) =>
        val request = readJsonBody[UpdateDictionaryNamespaceCollaboratorsRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.updateDictionaryNamespaceCollaborators(
            namespacePrefix = request.namespacePrefix,
            coOwnerPlayerIds = request.coOwners,
            editorPlayerIds = request.editors,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("dictionary", "namespaces", "context")) =>
        val request = readJsonBody[UpdateDictionaryNamespaceContextRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.updateDictionaryNamespaceContext(
            namespacePrefix = request.namespacePrefix,
            contextClubId = request.contextClub,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("dictionary", "namespaces", "reminders", "process")) =>
        val request = readJsonBody[ProcessDictionaryNamespaceRemindersRequest](exchange)
        sendJson(
          exchange,
          200,
          app.superAdminService.processDictionaryNamespaceReminders(
            actor = principal(request.operator),
            asOf = request.parsedAsOf.getOrElse(Instant.now()),
            dueSoonWindow = java.time.Duration.ofHours(request.dueSoonHours.toLong),
            reminderInterval = java.time.Duration.ofHours(request.reminderIntervalHours.toLong),
            escalationGrace = java.time.Duration.ofHours(request.escalationGraceHours.toLong)
          )
        )
      case ("POST", Vector("dictionary", "namespaces", "revoke")) =>
        val request = readJsonBody[RevokeDictionaryNamespaceRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.revokeDictionaryNamespace(
            namespacePrefix = request.namespacePrefix,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("GET", Vector("dictionary", key)) =>
        sendOption(exchange, app.globalDictionaryRepository.findByKey(key))
      case ("POST", Vector("admin", "dictionary")) =>
        val request = readJsonBody[UpsertDictionaryRequest](exchange)
        sendJson(
          exchange,
          201,
          app.superAdminService.upsertDictionary(
            key = request.key,
            value = request.value,
            actor = principal(request.operator),
            note = request.note
          )
        )
      case ("POST", Vector("admin", "players", playerId, "ban")) =>
        val request = readJsonBody[BanPlayerRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.banPlayer(
            playerId = PlayerId(playerId),
            reason = request.reason,
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("admin", "clubs", clubId, "dissolve")) =>
        val request = readJsonBody[DissolveClubRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.dissolveClub(
            clubId = ClubId(clubId),
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("admin", "players", playerId, "super-admin")) =>
        val request = readJsonBody[GrantSuperAdminRequest](exchange)
        sendOption(
          exchange,
          app.superAdminService.grantSuperAdmin(
            playerId = PlayerId(playerId),
            actor = principal(request.operator)
          )
        )
      case ("POST", Vector("admin", "advanced-stats", "recompute")) =>
        val request = readJsonBody[RecomputeAdvancedStatsRequest](exchange)
        val operator = principal(request.operator)
        app.authorizationService.requirePermission(operator, Permission.ManageGlobalDictionary)
        val requestedAt = Instant.now()
        val tasks =
          (request.ownerType, request.ownerId) match
            case (Some("player"), Some(ownerId)) =>
              Vector(
                app.advancedStatsPipelineService.enqueueOwnerRecompute(
                  owner = DashboardOwner.Player(PlayerId(ownerId)),
                  reason = request.reason.getOrElse("manual-targeted-recompute"),
                  requestedAt = requestedAt
                )
              )
            case (Some("club"), Some(ownerId)) =>
              Vector(
                app.advancedStatsPipelineService.enqueueOwnerRecompute(
                  owner = DashboardOwner.Club(ClubId(ownerId)),
                  reason = request.reason.getOrElse("manual-targeted-recompute"),
                  requestedAt = requestedAt
                )
              )
            case (Some(other), Some(_)) =>
              throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
            case _ =>
              request.parsedMode match
                case AdvancedStatsBackfillMode.Full =>
                  app.advancedStatsPipelineService.enqueueFullRecompute(
                    requestedAt = requestedAt,
                    reason = request.reason.getOrElse("manual-full-recompute")
                  )
                case mode =>
                  app.advancedStatsPipelineService.enqueueBackfill(
                    mode = mode,
                    requestedAt = requestedAt,
                    reason = request.reason.getOrElse(s"manual-${mode.toString.toLowerCase}-backfill"),
                    limit = request.limit
                  )
        sendJson(exchange, 202, tasks)
      case ("POST", Vector("admin", "advanced-stats", "process")) =>
        val request = readJsonBody[ProcessAdvancedStatsTasksRequest](exchange)
        val operator = principal(request.operator)
        app.authorizationService.requirePermission(operator, Permission.ManageGlobalDictionary)
        sendJson(
          exchange,
          200,
          app.advancedStatsPipelineService.processPending(limit = request.limit, processedAt = Instant.now())
        )

      case _ =>
        sendJson(exchange, 404, ApiError(s"Unsupported route: $method $path"))

  private def principal(playerId: PlayerId): AccessPrincipal =
    app.playerRepository
      .findById(playerId)
      .map(_.asPrincipal)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

  private def guestPrincipal(sessionId: GuestSessionId): AccessPrincipal =
    app.guestSessionRepository
      .findById(sessionId)
      .map(AccessPrincipal.guest)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))

  private def requestActor(
      guestSessionId: Option[GuestSessionId],
      operatorId: Option[PlayerId]
  ): AccessPrincipal =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    guestSessionId.map(guestPrincipal)
      .orElse(operatorId.map(principal))
      .getOrElse(AccessPrincipal.guest())

  private def queryPrincipal(exchange: HttpExchange): AccessPrincipal =
    val operatorId = queryParam(exchange, "operatorId")
      .map(PlayerId(_))
      .getOrElse(throw IllegalArgumentException("Query parameter operatorId is required"))
    principal(operatorId)

  private def queryParam(exchange: HttpExchange, key: String): Option[String] =
    val rawQuery = Option(exchange.getRequestURI.getRawQuery).getOrElse("")
    rawQuery
      .split("&")
      .toVector
      .filter(_.nonEmpty)
      .flatMap { entry =>
        entry.split("=", 2).toList match
          case rawKey :: rawValue :: Nil =>
            Some(
              URLDecoder.decode(rawKey, StandardCharsets.UTF_8) ->
                URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
            )
          case rawKey :: Nil =>
            Some(URLDecoder.decode(rawKey, StandardCharsets.UTF_8) -> "")
          case _ =>
            None
      }
      .collectFirst { case (`key`, value) => value }

  private def queryIntParam(exchange: HttpExchange, key: String): Option[Int] =
    queryParam(exchange, key).filter(_.nonEmpty).map { value =>
      Try(value.toInt).getOrElse(throw IllegalArgumentException(s"Query parameter $key must be an integer"))
    }

  private def queryBooleanParam(exchange: HttpExchange, key: String): Option[Boolean] =
    queryParam(exchange, key).filter(_.nonEmpty).map {
      case value if value.equalsIgnoreCase("true")  => true
      case value if value.equalsIgnoreCase("false") => false
      case _ =>
        throw IllegalArgumentException(s"Query parameter $key must be true or false")
    }

  private def pageQuery(
      exchange: HttpExchange,
      defaultLimit: Int = 20,
      maxLimit: Int = 100
  ): PageQuery =
    val limit = queryIntParam(exchange, "limit").getOrElse(defaultLimit)
    val offset = queryIntParam(exchange, "offset").getOrElse(0)
    require(limit > 0, "Query parameter limit must be positive")
    require(offset >= 0, "Query parameter offset must be non-negative")
    PageQuery(limit = math.min(limit, maxLimit), offset = offset)

  private def activeFilters(exchange: HttpExchange, keys: String*): Map[String, String] =
    keys.flatMap(key => queryParam(exchange, key).filter(_.nonEmpty).map(key -> _)).toMap

  private def sendPagedJson[T: Writer](
      exchange: HttpExchange,
      items: Vector[T],
      appliedFilters: Map[String, String] = Map.empty,
      defaultLimit: Int = 20,
      maxLimit: Int = 100
  ): Unit =
    val query = pageQuery(exchange, defaultLimit, maxLimit)
    val pagedItems = items.slice(query.offset, query.offset + query.limit)
    sendJson(
      exchange,
      200,
      PagedResponse(
        items = pagedItems,
        total = items.size,
        limit = query.limit,
        offset = query.offset,
        hasMore = query.offset + pagedItems.size < items.size,
        appliedFilters = appliedFilters
      )
    )

  private def parseEnum[E](label: String, value: String)(parse: String => E): E =
    Try(parse(value)).getOrElse(throw IllegalArgumentException(s"Invalid $label: $value"))

  private def containsIgnoreCase(value: String, fragment: String): Boolean =
    value.toLowerCase.contains(fragment.toLowerCase)

  private def readJsonBody[T: Reader](exchange: HttpExchange): T =
    val body = Using.resource(exchange.getRequestBody) { inputStream =>
      String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
    }

    if body.trim.isEmpty then
      throw IllegalArgumentException("Request body is required")
    else read[T](body)

  private def readOptionalJsonBody[T: Reader](exchange: HttpExchange): Option[T] =
    val body = Using.resource(exchange.getRequestBody) { inputStream =>
      String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
    }

    Option(body.trim).filter(_.nonEmpty).map(body => read[T](body))

  private def sendOption[T: Writer](exchange: HttpExchange, value: Option[T]): Unit =
    value match
      case Some(actual) => sendJson(exchange, 200, actual)
      case None         => sendJson(exchange, 404, ApiError("Resource not found"))

  private def sendJson[T: Writer](exchange: HttpExchange, statusCode: Int, payload: T): Unit =
    val json = write(payload, indent = 2)
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(statusCode, bytes.length.toLong)
    Using.resource(exchange.getResponseBody) { output =>
      output.write(bytes)
    }

