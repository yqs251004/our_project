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
import riichinexus.microservices.tournament.api.requests.ManagementRequests.given
import riichinexus.microservices.tournament.api.requests.SettlementRequests.given
import riichinexus.microservices.tournament.api.requests.StageRequests.given
import riichinexus.microservices.tournament.api.requests.TableRequests.given
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.objects.*
import riichinexus.api.http.RouteSupport

private[router] object TournamentManagementRoutes:
  def routes(support: RouteSupport, deps: TournamentRouterDependencies): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
    case req @ POST -> Root / "tournaments" =>
      support.handled {
        support.readJsonBody[CreateTournamentRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, TournamentManagementApi.createTournament(deps.service, request))
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "publish" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.publishTournament(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "start" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.startTournament(deps.service, support.principal, TournamentId(tournamentId), request)
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "settle" =>
      support.handled {
        support.readJsonBody[SettleTournamentRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            TournamentSettlementApi.settleTournament(deps.service, support.principal, TournamentId(tournamentId), request)
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "settlements" / settlementId / "finalize" =>
      support.handled {
        support.readJsonBody[FinalizeTournamentSettlementRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentSettlementApi.finalizeSettlement(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              SettlementSnapshotId(settlementId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "players" / playerId =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.registerPlayer(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              PlayerId(playerId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "clubs" / clubId =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.registerClub(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              ClubId(clubId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "clubs" / clubId / "remove" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.removeClubParticipation(
              deps.service,
              deps.views,
              support.principal,
              TournamentId(tournamentId),
              ClubId(clubId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "whitelist" / "players" / playerId =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.whitelistPlayer(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              PlayerId(playerId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "whitelist" / "clubs" / clubId =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.whitelistClub(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              ClubId(clubId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "admins" =>
      support.handled {
        support.readJsonBody[AssignTournamentAdminRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.assignTournamentAdmin(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              request
            )
          )
        }
      }

    case req @ POST -> Root / "tournaments" / tournamentId / "admins" / playerId / "revoke" =>
      support.handled {
        support.readJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            TournamentManagementApi.revokeTournamentAdmin(
              deps.service,
              support.principal,
              TournamentId(tournamentId),
              PlayerId(playerId),
              request
            )
          )
        }
      }
    }
