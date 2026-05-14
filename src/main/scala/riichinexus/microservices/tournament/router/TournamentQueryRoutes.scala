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

private[router] object TournamentQueryRoutes:
  def routes(support: RouteSupport, deps: TournamentRouterDependencies): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "tournaments" =>
      support.handled {
        val query = TournamentListQuery(
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(TournamentStatus.valueOf)
          ),
          adminId = support.queryParam(req, "adminId").filter(_.nonEmpty).map(PlayerId(_)),
          organizer = support.queryParam(req, "organizer").filter(_.nonEmpty)
        )
        val tournaments = TournamentQueryApi.listTournaments(deps.tables, query)
        support.pagedJsonResponse(req, tournaments, support.activeFilters(req, "status", "adminId", "organizer"))
      }

    case GET -> Root / "tournaments" / tournamentId =>
      support.handled(
        support.optionJsonResponse(TournamentQueryApi.findTournamentDetail(deps.views, TournamentId(tournamentId)))
      )

    case GET -> Root / "tournaments" / tournamentId / "stages" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          TournamentQueryApi.stageDirectory(deps.tables, deps.views, TournamentId(tournamentId))
        )
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "whitelist" =>
      support.handled {
        val query = TournamentWhitelistQuery(
          participantKind = support.queryParam(req, "participantKind")
            .filter(_.nonEmpty)
            .map(support.parseEnum("participantKind", _)(TournamentParticipantKind.valueOf)),
          playerId = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_)),
          clubId = support.queryParam(req, "clubId").filter(_.nonEmpty).map(ClubId(_))
        )
        val whitelist = TournamentQueryApi.whitelist(deps.tables, TournamentId(tournamentId), query)
        support.pagedJsonResponse(req, whitelist, support.activeFilters(req, "participantKind", "playerId", "clubId"))
      }

    case req @ GET -> Root / "tournaments" / tournamentId / "settlements" =>
      support.handled {
        val query = TournamentSettlementQuery(
          stageId = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_)),
          status = support.queryParam(req, "status")
            .filter(_.nonEmpty)
            .map(value => TournamentSettlementStatus.valueOf(value)),
          championId = support.queryParam(req, "championId").filter(_.nonEmpty).map(PlayerId(_))
        )
        val settlements = TournamentSettlementApi.listSettlements(deps.tables, TournamentId(tournamentId), query)
        support.pagedJsonResponse(req, settlements, support.activeFilters(req, "stageId", "status", "championId"))
      }

    case GET -> Root / "tournaments" / tournamentId / "settlements" / stageId =>
      support.handled {
        support.optionJsonResponse(
          TournamentSettlementApi.findSettlement(deps.tables, TournamentId(tournamentId), TournamentStageId(stageId))
        )
      }
    }
