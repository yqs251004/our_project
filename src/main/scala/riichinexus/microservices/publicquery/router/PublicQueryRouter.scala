package riichinexus.microservices.publicquery.router

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.ClubViewAssembler
import riichinexus.microservices.publicquery.api.{
  PublicClubApi,
  PublicLeaderboardApi,
  PublicQueryService,
  PublicScheduleApi,
  PublicTournamentApi
}
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.given
import riichinexus.microservices.publicquery.objects.*
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.tournament.api.TournamentViewAssembler
import riichinexus.api.http.RouteSupport

object PublicQueryRouter:
  private final case class Dependencies(
      tables: PublicQueryTables,
      service: PublicQueryService,
      clubViews: ClubViewAssembler,
      tournamentViews: TournamentViewAssembler
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.publicQueryModule
    Dependencies(
      tables = module.tables,
      service = module.service,
      clubViews = module.clubViews,
      tournamentViews = module.tournamentViews
    )

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "public" / "schedules" =>
      support.handled {
        val query = PublicScheduleQuery(
          tournamentStatus = support.queryParam(req, "tournamentStatus").filter(_.nonEmpty).map(
            support.parseEnum("tournamentStatus", _)(TournamentStatus.valueOf)
          ),
          stageStatus = support.queryParam(req, "stageStatus").filter(_.nonEmpty).map(
            support.parseEnum("stageStatus", _)(StageStatus.valueOf)
          )
        )
        val schedules = PublicScheduleApi.listSchedules(deps.service, query)
        support.pagedJsonResponse(req, schedules, support.activeFilters(req, "tournamentStatus", "stageStatus"))
      }

    case req @ GET -> Root / "public" / "tournaments" =>
      support.handled {
        val query = PublicTournamentQuery(
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(TournamentStatus.valueOf)
          ),
          organizer = support.queryParam(req, "organizer").filter(_.nonEmpty)
        )
        val summaries = PublicTournamentApi.listTournamentSummaries(deps.tables, deps.tournamentViews, query)
        support.pagedJsonResponse(req, summaries, support.activeFilters(req, "status", "organizer"))
      }

    case GET -> Root / "public" / "tournaments" / tournamentId =>
      support.handled(support.optionJsonResponse(PublicTournamentApi.detail(deps.tournamentViews, TournamentId(tournamentId))))

    case req @ GET -> Root / "public" / "clubs" =>
      support.handled {
        val query = PublicClubDirectoryQuery(
          name = support.queryParam(req, "name").filter(_.nonEmpty),
          relation = support.queryParam(req, "relation").filter(_.nonEmpty).map(
            support.parseEnum("relation", _)(ClubRelationKind.valueOf)
          )
        )
        val clubs = PublicClubApi.listClubs(deps.service, query, support.containsIgnoreCase)
        support.pagedJsonResponse(req, clubs, support.activeFilters(req, "name", "relation"))
      }

    case GET -> Root / "public" / "clubs" / clubId =>
      support.handled(support.optionJsonResponse(PublicClubApi.detail(deps.clubViews, ClubId(clubId))))

    case req @ GET -> Root / "public" / "leaderboards" / "players" =>
      support.handled {
        val query = PublicPlayerLeaderboardQuery(
          clubId = support.queryParam(req, "clubId").filter(_.nonEmpty).map(ClubId(_)),
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(PlayerStatus.valueOf)
          )
        )
        val leaderboard = PublicLeaderboardApi.playerLeaderboard(deps.service, query)
        support.pagedJsonResponse(req, leaderboard, support.activeFilters(req, "clubId", "status"))
      }

    case req @ GET -> Root / "public" / "leaderboards" / "clubs" =>
      support.handled {
        val query = PublicClubLeaderboardQuery(
          name = support.queryParam(req, "name").filter(_.nonEmpty)
        )
        val leaderboard = PublicLeaderboardApi.clubLeaderboard(deps.service, query, support.containsIgnoreCase)
        support.pagedJsonResponse(req, leaderboard, support.activeFilters(req, "name"))
      }
  }


