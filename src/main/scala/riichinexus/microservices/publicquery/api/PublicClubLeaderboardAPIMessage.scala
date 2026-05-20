package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{AccessPrincipal, Permission}
import riichinexus.microservices.publicquery.objects.apiTypes.ClubLeaderboardEntry
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class PublicClubLeaderboardAPIMessage(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubLeaderboardEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubLeaderboardEntry]] =
    IO {
      val module = context.support.publicQueryModule
      context.support.routeContext.authorizationService
        .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicLeaderboard)

      val leaderboard = module.tables.publicClubLeaderboard(Int.MaxValue)
        .filter(entry => name.filter(_.nonEmpty).forall(context.support.containsIgnoreCase(entry.name, _)))
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = leaderboard.slice(resolvedOffset, resolvedOffset + boundedLimit)

      PagedResponse(
        items = page,
        total = leaderboard.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < leaderboard.size,
        appliedFilters = Vector(name.filter(_.nonEmpty).map("name" -> _)).flatten.toMap
      )
    }
