package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.Permission
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.club.domain.model.ClubRelationKind
import riichinexus.microservices.publicquery.objects.apiTypes.PublicClubDirectoryEntry
import riichinexus.microservices.publicquery.domain.PublicDirectoryQueries
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListPublicClubsAPIMessage(
    name: Option[String] = None,
    relation: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicClubDirectoryEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicClubDirectoryEntry]] =
    for
      query <- IO(resolveQuery(context))
      clubs <- IO(listClubs(context, query))
    yield PagedResponse.fromItems(clubs, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedClubDirectoryQuery =
    context.support.authorizationService
      .requirePermission(AccessPrincipal.guest(), Permission.ViewClubDirectory)
    ResolvedClubDirectoryQuery(
      name = name.filter(_.nonEmpty),
      relation = relation.filter(_.nonEmpty).map(
        context.support.parseEnum("relation", _)(ClubRelationKind.valueOf)
      ),
      appliedFilters = Vector(
        name.filter(_.nonEmpty).map("name" -> _),
        relation.filter(_.nonEmpty).map("relation" -> _)
      ).flatten.toMap
    )

  private def listClubs(
      context: ApiPlanContext,
      query: ResolvedClubDirectoryQuery
  ): Vector[PublicClubDirectoryEntry] =
    PublicDirectoryQueries.publicClubDirectory(context.connection)
      .filter(club => query.name.forall(context.support.containsIgnoreCase(club.name, _)))
      .filter(club => query.relation.forall(relationKind => club.relations.exists(_.relation == relationKind.toString)))
      .sortBy(_.name)

  private final case class ResolvedClubDirectoryQuery(
      name: Option[String],
      relation: Option[ClubRelationKind],
      appliedFilters: Map[String, String]
  )
