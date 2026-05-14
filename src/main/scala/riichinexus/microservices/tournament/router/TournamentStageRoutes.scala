package riichinexus.microservices.tournament.router

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.shared.api.requests.OperatorRequest.given
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.api.responses.TournamentOperationResponses.given
import riichinexus.microservices.tournament.api.requests.StageRequests.given
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.objects.*
import riichinexus.api.http.RouteSupport

private[router] object TournamentStageRoutes:
  def routes(support: RouteSupport, deps: TournamentRouterDependencies): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "tournaments" / tournamentId / "stages" =>
        support.handled {
          support.readJsonBody[CreateTournamentStageRequest](req).flatMap { request =>
            support.optionJsonResponse(
              TournamentStageApi.addStage(deps.service, support.principal, TournamentId(tournamentId), request)
            )
          }
        }

      case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "rules" =>
        support.handled {
          support.readJsonBody[ConfigureStageRulesRequest](req).flatMap { request =>
            support.optionJsonResponse(
              TournamentStageApi.configureStageRules(
                deps.service,
                support.principal,
                TournamentId(tournamentId),
                TournamentStageId(stageId),
                request
              )
            )
          }
        }

      case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "lineups" =>
        support.handled {
          support.readJsonBody[SubmitStageLineupRequest](req).flatMap { request =>
            support.optionJsonResponse(
              TournamentStageApi.submitLineup(
                deps.service,
                deps.views,
                support.principal,
                TournamentId(tournamentId),
                TournamentStageId(stageId),
                request
              )
            )
          }
        }

      case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "schedule" =>
        support.handled {
          support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
            support.optionJsonResponse(
              TournamentStageApi.scheduleStageTables(
                deps.service,
                deps.views,
                support.principal,
                TournamentId(tournamentId),
                TournamentStageId(stageId),
                request
              )
            )
          }
        }

      case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "standings" =>
        support.handled {
          support.jsonResponse(
            Status.Ok,
            TournamentStageApi.stageStandings(deps.stageQueries, TournamentId(tournamentId), TournamentStageId(stageId))
          )
        }

      case req @ GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "tables" =>
        support.handled {
          val query = StageTableQuery(
            status = support.queryParam(req, "status").filter(_.nonEmpty).map(
              support.parseEnum("status", _)(TableStatus.valueOf)
            ),
            roundNumber = support.queryIntParam(req, "roundNumber"),
            playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
          )
          val tables = TournamentStageApi.stageTables(
            deps.tables,
            TournamentId(tournamentId),
            TournamentStageId(stageId),
            query
          )
          support.pagedJsonResponse(req, tables, support.activeFilters(req, "status", "roundNumber", "playerId"))
        }

      case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "advancement" =>
        support.handled {
          support.jsonResponse(
            Status.Ok,
            TournamentStageApi.stageAdvancementPreview(
              deps.stageQueries,
              TournamentId(tournamentId),
              TournamentStageId(stageId)
            )
          )
        }

      case GET -> Root / "tournaments" / tournamentId / "stages" / stageId / "bracket" =>
        support.handled {
          support.jsonResponse(
            Status.Ok,
            TournamentStageApi.stageKnockoutBracket(
              deps.stageQueries,
              TournamentId(tournamentId),
              TournamentStageId(stageId)
            )
          )
        }

      case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "advance" =>
        support.handled {
          support.readJsonBody[AdvanceKnockoutStageRequest](req).flatMap { request =>
            support.jsonResponse(
              Status.Ok,
              TournamentStageApi.advanceKnockoutStage(
                deps.service,
                support.principal,
                TournamentId(tournamentId),
                TournamentStageId(stageId),
                request
              )
            )
          }
        }

      case req @ POST -> Root / "tournaments" / tournamentId / "stages" / stageId / "complete" =>
        support.handled {
          support.readJsonBody[CompleteStageRequest](req).flatMap { request =>
            support.optionJsonResponse(
              TournamentStageApi.completeStage(
                deps.service,
                support.principal,
                TournamentId(tournamentId),
                TournamentStageId(stageId),
                request
              )
            )
          }
        }
    }
