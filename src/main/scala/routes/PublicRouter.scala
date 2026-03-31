package routes

import java.time.Instant

import api.contracts.ApiContracts.*
import api.contracts.JsonSupport.given
import cats.effect.IO
import model.DomainModels.*
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*

object PublicRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "session" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          support.resolveCurrentSessionView(
            operatorId = support.queryParam(req, "operatorId").filter(_.nonEmpty).map(PlayerId(_)),
            guestSessionId = support.queryParam(req, "guestSessionId").filter(_.nonEmpty).map(GuestSessionId(_))
          )
        )
      }

    case req @ GET -> Root / "players" =>
      support.handled {
        val clubIdFilter = support.queryParam(req, "clubId").filter(_.nonEmpty).map(ClubId(_))
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(PlayerStatus.valueOf)
        )
        val nicknameFilter = support.queryParam(req, "nickname").filter(_.nonEmpty)
        val players = support.app.playerRepository.findAll()
          .filter(player => clubIdFilter.forall(player.boundClubIds.contains))
          .filter(player => statusFilter.forall(_ == player.status))
          .filter(player => nicknameFilter.forall(support.containsIgnoreCase(player.nickname, _)))
          .sortBy(player => (player.nickname, player.id.value))
        support.pagedJsonResponse(req, players, support.activeFilters(req, "clubId", "status", "nickname"))
      }

    case req @ GET -> Root / "players" / "me" =>
      support.handled {
        val operatorId = support.queryParam(req, "operatorId")
          .filter(_.nonEmpty)
          .map(PlayerId(_))
          .getOrElse(throw IllegalArgumentException("Query parameter operatorId is required"))
        support.optionJsonResponse(support.app.playerRepository.findById(operatorId))
      }

    case GET -> Root / "players" / playerId =>
      support.handled(support.optionJsonResponse(support.app.playerRepository.findById(PlayerId(playerId))))

    case req @ POST -> Root / "players" =>
      support.handled {
        support.readJsonBody[CreatePlayerRequest](req).flatMap { request =>
          val player = support.app.playerService.registerPlayer(
            userId = request.userId,
            nickname = request.nickname,
            rank = request.toRankSnapshot,
            initialElo = request.initialElo
          )
          support.jsonResponse(Status.Created, player)
        }
      }

    case req @ GET -> Root / "guest-sessions" =>
      support.handled {
        val activeOnly = support.queryBooleanParam(req, "activeOnly")
        val sessions = support.app.guestSessionRepository.findAll()
          .filter(session => activeOnly.forall(flag => !flag || session.canAuthenticate(Instant.now())))
          .sortBy(session => (session.createdAt, session.id.value))
        support.pagedJsonResponse(req, sessions, support.activeFilters(req, "activeOnly"))
      }

    case req @ POST -> Root / "guest-sessions" =>
      support.handled {
        support.readOptionalJsonBody[CreateGuestSessionRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Created,
            support.app.guestSessionService.createSession(
              displayName = request.flatMap(_.displayName).getOrElse("guest"),
              ttl = java.time.Duration.ofHours(request.flatMap(_.ttlHours).getOrElse(24 * 30).toLong),
              deviceFingerprint = request.flatMap(_.deviceFingerprint)
            )
          )
        }
      }

    case GET -> Root / "guest-sessions" / sessionId =>
      support.handled(support.optionJsonResponse(support.app.guestSessionService.findSession(GuestSessionId(sessionId))))

    case req @ POST -> Root / "guest-sessions" / sessionId / "revoke" =>
      support.handled {
        support.readOptionalJsonBody[RevokeGuestSessionRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.guestSessionService.revokeSession(
              GuestSessionId(sessionId),
              request.flatMap(_.reason).filter(_.trim.nonEmpty).getOrElse("revoked-by-operator")
            )
          )
        }
      }

    case req @ POST -> Root / "guest-sessions" / sessionId / "upgrade" =>
      support.handled {
        support.readJsonBody[UpgradeGuestSessionRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.guestSessionService.upgradeSession(GuestSessionId(sessionId), request.player)
          )
        }
      }

    case req @ GET -> Root / "public" / "schedules" =>
      support.handled {
        val tournamentStatusFilter = support.queryParam(req, "tournamentStatus").filter(_.nonEmpty).map(
          support.parseEnum("tournamentStatus", _)(TournamentStatus.valueOf)
        )
        val stageStatusFilter = support.queryParam(req, "stageStatus").filter(_.nonEmpty).map(
          support.parseEnum("stageStatus", _)(StageStatus.valueOf)
        )
        val schedules = support.app.publicQueryService.publicSchedules()
          .filter(schedule => tournamentStatusFilter.forall(_ == schedule.tournamentStatus))
          .filter(schedule => stageStatusFilter.forall(_ == schedule.stageStatus))
          .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))
        support.pagedJsonResponse(req, schedules, support.activeFilters(req, "tournamentStatus", "stageStatus"))
      }

    case req @ GET -> Root / "public" / "tournaments" =>
      support.handled {
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(TournamentStatus.valueOf)
        )
        val organizerFilter = support.queryParam(req, "organizer").filter(_.nonEmpty)
        val tournaments = support.app.tournamentRepository.findAll()
          .filter(_.status != TournamentStatus.Draft)
          .filter(tournament => statusFilter.forall(_ == tournament.status))
          .filter(tournament => organizerFilter.forall(support.containsIgnoreCase(tournament.organizer, _)))
          .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))
          .map(support.buildPublicTournamentSummaryView)
        support.pagedJsonResponse(req, tournaments, support.activeFilters(req, "status", "organizer"))
      }

    case GET -> Root / "public" / "tournaments" / tournamentId =>
      support.handled(support.optionJsonResponse(support.buildPublicTournamentDetailView(TournamentId(tournamentId))))

    case req @ GET -> Root / "public" / "clubs" =>
      support.handled {
        val nameFilter = support.queryParam(req, "name").filter(_.nonEmpty)
        val relationFilter = support.queryParam(req, "relation").filter(_.nonEmpty).map(
          support.parseEnum("relation", _)(ClubRelationKind.valueOf)
        )
        val clubs = support.app.publicQueryService.publicClubDirectory()
          .filter(club => nameFilter.forall(support.containsIgnoreCase(club.name, _)))
          .filter(club => relationFilter.forall(relation => club.relations.exists(_.relation == relation)))
          .sortBy(_.name)
        support.pagedJsonResponse(req, clubs, support.activeFilters(req, "name", "relation"))
      }

    case GET -> Root / "public" / "clubs" / clubId =>
      support.handled(support.optionJsonResponse(support.buildPublicClubDetailView(ClubId(clubId))))

    case req @ GET -> Root / "public" / "leaderboards" / "players" =>
      support.handled {
        val clubIdFilter = support.queryParam(req, "clubId").filter(_.nonEmpty).map(ClubId(_))
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(PlayerStatus.valueOf)
        )
        val leaderboard = support.app.publicQueryService.publicPlayerLeaderboard(support.app.playerRepository.findAll().size)
          .filter(entry => clubIdFilter.forall(entry.clubIds.contains))
          .filter(entry => statusFilter.forall(_ == entry.status))
        support.pagedJsonResponse(req, leaderboard, support.activeFilters(req, "clubId", "status"))
      }

    case req @ GET -> Root / "public" / "leaderboards" / "clubs" =>
      support.handled {
        val nameFilter = support.queryParam(req, "name").filter(_.nonEmpty)
        val leaderboard = support.app.publicQueryService.publicClubLeaderboard(support.app.clubRepository.findAll().size)
          .filter(entry => nameFilter.forall(support.containsIgnoreCase(entry.name, _)))
        support.pagedJsonResponse(req, leaderboard, support.activeFilters(req, "name"))
      }
  }
