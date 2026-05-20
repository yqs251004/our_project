package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{AccessPrincipal, ClubRelationKind, Permission}
import riichinexus.microservices.publicquery.objects.apiTypes.PublicClubDirectoryEntry
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListPublicClubsAPIMessage(
    name: Option[String] = None,
    relation: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicClubDirectoryEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicClubDirectoryEntry]] =
    IO {
      val module = context.support.publicQueryModule
      context.support.routeContext.authorizationService
        .requirePermission(AccessPrincipal.guest(), Permission.ViewClubDirectory)

      val parsedRelation = relation.filter(_.nonEmpty).map(
        context.support.parseEnum("relation", _)(ClubRelationKind.valueOf)
      )
      val clubs = module.tables.publicClubDirectory()
        .filter(club => name.filter(_.nonEmpty).forall(context.support.containsIgnoreCase(club.name, _)))
        .filter(club => parsedRelation.forall(relationKind => club.relations.exists(_.relation == relationKind)))
        .sortBy(_.name)
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = clubs.slice(resolvedOffset, resolvedOffset + boundedLimit)

      PagedResponse(
        items = page,
        total = clubs.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < clubs.size,
        appliedFilters = Vector(
          name.filter(_.nonEmpty).map("name" -> _),
          relation.filter(_.nonEmpty).map("relation" -> _)
        ).flatten.toMap
      )
    }
