package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{ClubId, Permission, PlayerId}
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.objects.{Player, PlayerStatus}
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.club.objects.apiTypes.{PublicClubDirectoryEntry, PublicClubRelationView}
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
      clubs <- IO(publicClubs(context))
      playersById <- IO(publicClubPlayersById(context, clubs))
      relatedClubsById <- IO(publicRelatedClubsById(context, clubs))
      entries <- IO(publicClubDirectoryEntries(clubs, playersById, relatedClubsById))
      filteredEntries <- IO(filterPublicClubDirectoryEntries(context, entries, query))
    yield PagedResponse.fromItems(filteredEntries, limit, offset, query.appliedFilters)(identity)

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

  private def publicClubs(context: ApiPlanContext): Vector[Club] =
    ClubTable.findActive(context.connection).sortBy(_.name)

  private def publicClubPlayersById(
      context: ApiPlanContext,
      clubs: Vector[Club]
  ): Map[PlayerId, Player] =
    PlayerTable
      .findByIds(context.connection, clubs.flatMap(_.members).distinct)
      .map(player => player.id -> player)
      .toMap

  private def publicRelatedClubsById(
      context: ApiPlanContext,
      clubs: Vector[Club]
  ): Map[ClubId, Club] =
    val activeClubIds = clubs.map(_.id).toSet
    ClubTable
      .findByIds(
        context.connection,
        clubs.flatMap(_.relations.map(_.targetClubId)).distinct.filterNot(activeClubIds.contains)
      )
      .map(club => club.id -> club)
      .toMap

  private def publicClubDirectoryEntries(
      clubs: Vector[Club],
      playersById: Map[PlayerId, Player],
      relatedClubsById: Map[ClubId, Club]
  ): Vector[PublicClubDirectoryEntry] =
    val clubsById = clubs.map(club => club.id -> club).toMap ++ relatedClubsById

    clubs.map { club =>
      val activeMemberCount = club.members.count(playerId =>
        playersById.get(playerId).exists(_.status == PlayerStatus.Active)
      )
      val rivalryTargets = club.relations.filter(_.relation == ClubRelationKind.Rivalry)
      val strongestRival = rivalryTargets
        .flatMap(relation => clubsById.get(relation.targetClubId))
        .sortBy(rival => (-rival.powerRating, rival.name))
        .headOption
      PublicClubDirectoryEntry(
        clubId = club.id,
        name = club.name,
        memberCount = club.members.size,
        activeMemberCount = activeMemberCount,
        adminCount = club.admins.size,
        powerRating = round2(club.powerRating),
        totalPoints = club.totalPoints,
        treasuryBalance = club.treasuryBalance,
        pointPool = club.pointPool,
        allianceCount = club.relations.count(_.relation == ClubRelationKind.Alliance),
        rivalryCount = rivalryTargets.size,
        strongestRivalClubId = strongestRival.map(_.id),
        strongestRivalPower = strongestRival.map(rival => round2(rival.powerRating)),
        honorTitles = club.honors.map(_.title).sorted,
        relations = club.relations.map(PublicClubRelationView.fromDomain)
      )
    }

  private def filterPublicClubDirectoryEntries(
      context: ApiPlanContext,
      entries: Vector[PublicClubDirectoryEntry],
      query: ResolvedClubDirectoryQuery
  ): Vector[PublicClubDirectoryEntry] =
    entries
      .filter(club => query.name.forall(context.support.containsIgnoreCase(club.name, _)))
      .filter(club => query.relation.forall(relationKind => club.relations.exists(_.relation == relationKind.toString)))
      .sortBy(_.name)

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  private final case class ResolvedClubDirectoryQuery(
      name: Option[String],
      relation: Option[ClubRelationKind],
      appliedFilters: Map[String, String]
  )
