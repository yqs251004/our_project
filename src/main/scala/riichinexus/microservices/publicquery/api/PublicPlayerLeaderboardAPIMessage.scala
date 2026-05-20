package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{AccessPrincipal, ClubId, Permission, PlayerStatus}
import riichinexus.microservices.publicquery.objects.apiTypes.PlayerLeaderboardEntry
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class PublicPlayerLeaderboardAPIMessage(
    clubId: Option[String] = None,
    status: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerLeaderboardEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerLeaderboardEntry]] =
    IO {
      val module = context.support.publicQueryModule
      context.support.routeContext.authorizationService
        .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicLeaderboard)

      val parsedClubId = clubId.filter(_.nonEmpty).map(ClubId(_))
      val parsedStatus = status.filter(_.nonEmpty).map(
        context.support.parseEnum("status", _)(PlayerStatus.valueOf)
      )
      val leaderboard = module.tables.publicPlayerLeaderboard(Int.MaxValue)
        .filter(entry => parsedClubId.forall(entry.clubIds.contains))
        .filter(entry => parsedStatus.forall(_ == entry.status))
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
        appliedFilters = Vector(
          clubId.filter(_.nonEmpty).map("clubId" -> _),
          status.filter(_.nonEmpty).map("status" -> _)
        ).flatten.toMap
      )
    }
