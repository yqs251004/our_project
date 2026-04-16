package routes

import java.util.NoSuchElementException

import api.contracts.ApiContracts.*
import api.contracts.JsonSupport.given
import json.JsonCodecs.given
import riichinexus.api.ApiModels.given
import cats.effect.IO
import model.DomainModels.*
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*

object TournamentRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "tournaments" =>
      support.handled {
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(TournamentStatus.valueOf)
        )
        val adminIdFilter = support.queryParam(req, "adminId").filter(_.nonEmpty).map(PlayerId(_))
        val organizerFilter = support.queryParam(req, "organizer").filter(_.nonEmpty)
        val tournaments = support.app.tournamentRepository.findFiltered(
          status = statusFilter,
          adminId = adminIdFilter,
          organizer = organizerFilter
        )
          .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))
        support.pagedJsonResponse(req, tournaments, support.activeFilters(req, "status", "adminId", "organizer"))
      }

    case GET -> Root / "tournaments" / tournamentId =>
      support.handled(support.optionJsonResponse(support.buildTournamentDetailView(TournamentId(tournamentId))))

    case GET -> Root / "tournaments" / tournamentId / "stages" =>
      support.handled {
        val tournament = support.app.tournamentRepository
          .findById(TournamentId(tournamentId))
          .getOrElse(throw NoSuchElementException(s"Tournament $tournamentId was not found"))
        support.jsonResponse(Status.Ok, tournament.stages.sortBy(_.order).map(support.buildTournamentStageDirectoryEntry))
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "whitelist" =>
      support.handled {
        val whitelist = support.app.tournamentRepository
          .findById(TournamentId(tournamentId))
          .map(_.whitelist
            .filter(entry =>
              support.queryParam(req, "participantKind")
                .filter(_.nonEmpty)
                .map(support.parseEnum("participantKind", _)(TournamentParticipantKind.valueOf))
                .forall(_ == entry.participantKind)
            )
            .filter(entry =>
              support.queryParam(req, "playerId")
                .filter(_.nonEmpty)
                .forall(value => entry.playerId.contains(PlayerId(value)))
            )
            .filter(entry =>
              support.queryParam(req, "clubId")
                .filter(_.nonEmpty)
                .forall(value => entry.clubId.contains(ClubId(value)))
            )
          )
          .getOrElse(throw NoSuchElementException(s"Tournament $tournamentId was not found"))
        support.pagedJsonResponse(req, whitelist, support.activeFilters(req, "participantKind", "playerId", "clubId"))
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "settlements" =>
      support.handled {
        val settlements = support.app.tournamentSettlementRepository.findByTournament(TournamentId(tournamentId))
          .filter(snapshot =>
            support.queryParam(req, "stageId")
              .filter(_.nonEmpty)
              .forall(value => snapshot.stageId == TournamentStageId(value))
          )
          .filter(snapshot =>
            support.queryParam(req, "status")
              .filter(_.nonEmpty)
              .forall(value => snapshot.status == TournamentSettlementStatus.valueOf(value))
          )
          .filter(snapshot =>
            support.queryParam(req, "championId")
              .filter(_.nonEmpty)
              .forall(value => snapshot.championId == PlayerId(value))
          )
          .sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))
        support.pagedJsonResponse(req, settlements, support.activeFilters(req, "stageId", "status", "championId"))
      }

    case GET -> Root / "tournaments" / tournamentId / "settlements" / stageId =>
      support.handled {
        support.optionJsonResponse(
          support.app.tournamentSettlementRepository.findByTournamentAndStage(
            TournamentId(tournamentId),
            TournamentStageId(stageId)
          )
        )
      }

    case req @ POST -> Root / "tournaments" =>
      support.handled {
        support.readJsonBody[CreateTournamentRequest](req).flatMap { request =>
          val tournament = support.app.tournamentService.createTournament(
            name = request.name,
            organizer = request.organizer,
            startsAt = request.startsAt,
            endsAt = request.endsAt,
            stages = request.toStages,
            adminId = request.admin
          )
          support.jsonResponse(Status.Created, tournament)
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "publish" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.app.tournamentService.publishTournament(
            TournamentId(tournamentId),
            request.flatMap(_.operator).map(support.principal).getOrElse(AccessPrincipal.system)
          )
          support.optionJsonResponse(
            support.buildTournamentMutationView(
              TournamentId(tournamentId),
              Vector.empty
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "start" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.startTournament(
              TournamentId(tournamentId),
              request.flatMap(_.operator).map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "settle" =>
      support.handled {
        support.readJsonBody[SettleTournamentRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.tournamentService.settleTournament(
              tournamentId = TournamentId(tournamentId),
              finalStageId = request.stageId,
              prizePool = request.prizePool,
              payoutRatios = request.payoutRatios,
              houseFeeAmount = request.houseFeeAmount,
              clubShareRatio = request.clubShareRatio,
              adjustments = request.adjustments.map(_.adjustment),
              finalize = request.finalizeSettlement,
              note = request.note,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "settlements" / settlementId / "finalize" =>
      support.handled {
        support.readJsonBody[FinalizeTournamentSettlementRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.finalizeTournamentSettlement(
              tournamentId = TournamentId(tournamentId),
              settlementId = SettlementSnapshotId(settlementId),
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "players" / playerId =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.registerPlayer(
              TournamentId(tournamentId),
              PlayerId(playerId),
              request.flatMap(_.operator).map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "clubs" / clubId =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.app.tournamentService.registerClub(
            TournamentId(tournamentId),
            ClubId(clubId),
            request.flatMap(_.operator).map(support.principal).getOrElse(AccessPrincipal.system)
          )
          support.optionJsonResponse(
            support.buildTournamentMutationView(
              TournamentId(tournamentId),
              Vector.empty
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "clubs" / clubId / "remove" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.app.tournamentService.removeClubParticipation(
            tournamentId = TournamentId(tournamentId),
            clubId = ClubId(clubId),
            actor = request.flatMap(_.operator).map(support.principal).getOrElse(AccessPrincipal.system)
          )
          support.optionJsonResponse(
            support.buildTournamentMutationView(
              TournamentId(tournamentId),
              Vector.empty
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "whitelist" / "players" / playerId =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.whitelistPlayer(
              TournamentId(tournamentId),
              PlayerId(playerId),
              request.operator.map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "whitelist" / "clubs" / clubId =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.whitelistClub(
              TournamentId(tournamentId),
              ClubId(clubId),
              request.operator.map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "admins" =>
      support.handled {
        support.readJsonBody[AssignTournamentAdminRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.assignTournamentAdmin(
              tournamentId = TournamentId(tournamentId),
              playerId = request.player,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "admins" / playerId / "revoke" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.revokeTournamentAdmin(
              tournamentId = TournamentId(tournamentId),
              playerId = PlayerId(playerId),
              actor = request.operator.map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" =>
      support.handled {
        support.readJsonBody[CreateTournamentStageRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.addStage(
              tournamentId = TournamentId(tournamentId),
              stage = request.toStage,
              actor = request.operator.map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "rules" =>
      support.handled {
        support.readJsonBody[ConfigureStageRulesRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.configureStageRules(
              tournamentId = TournamentId(tournamentId),
              stageId = TournamentStageId(stageId),
              advancementRule = request.advancementRule,
              swissRule = request.swissRule,
              knockoutRule = request.knockoutRule,
              schedulingPoolSize = request.schedulingPoolSize,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "lineups" =>
      support.handled {
        support.readJsonBody[SubmitStageLineupRequest](req).flatMap { request =>
          support.app.tournamentService.submitLineup(
            tournamentId = TournamentId(tournamentId),
            stageId = TournamentStageId(stageId),
            submission = request.toSubmission,
            actor = support.principal(request.operator)
          )
          support.optionJsonResponse(
            support.buildTournamentMutationView(
              tournamentId = TournamentId(tournamentId),
              scheduledTables = Vector.empty
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "schedule" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          val scheduledTables = support.app.tournamentService.scheduleStageTables(
            TournamentId(tournamentId),
            TournamentStageId(stageId),
            request.flatMap(_.operator).map(support.principal).getOrElse(AccessPrincipal.system)
          )
          support.optionJsonResponse(
            support.buildTournamentMutationView(
              TournamentId(tournamentId),
              scheduledTables
            )
          )
        }
      }

    case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "standings" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          support.app.tournamentService.stageStandings(TournamentId(tournamentId), TournamentStageId(stageId))
        )
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "tables" =>
      support.handled {
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(TableStatus.valueOf)
        )
        val roundNumberFilter = support.queryIntParam(req, "roundNumber")
        val playerIdFilter = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tables = support.app.tableRepository.findByTournamentAndStage(TournamentId(tournamentId), TournamentStageId(stageId))
          .filter(table => statusFilter.forall(_ == table.status))
          .filter(table => roundNumberFilter.forall(_ == table.stageRoundNumber))
          .filter(table => playerIdFilter.forall(playerId => table.seats.exists(_.playerId == playerId)))
          .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
        support.pagedJsonResponse(req, tables, support.activeFilters(req, "status", "roundNumber", "playerId"))
      }

    case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "advancement" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          support.app.tournamentService.stageAdvancementPreview(TournamentId(tournamentId), TournamentStageId(stageId))
        )
      }

    case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "bracket" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          support.app.tournamentService.stageKnockoutBracket(TournamentId(tournamentId), TournamentStageId(stageId))
        )
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "advance" =>
      support.handled {
        support.readJsonBody[AdvanceKnockoutStageRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.tournamentService.advanceKnockoutStage(
              tournamentId = TournamentId(tournamentId),
              stageId = TournamentStageId(stageId),
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "complete" =>
      support.handled {
        support.readJsonBody[CompleteStageRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tournamentService.completeStage(
              tournamentId = TournamentId(tournamentId),
              stageId = TournamentStageId(stageId),
              actor = support.principal(request.operator)
            )
          )
        }
      }
  }


