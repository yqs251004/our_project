package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.relationmanagement.{ClubRelationKind, ClubRelationView}
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.PublicClubDirectoryEntry
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ListPublicClubsAPIMessage(
    name: Option[String] = None,
    relation: Option[ClubRelationKind] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicClubDirectoryEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicClubDirectoryEntry]] =
    for
      _ <- requirePublicDirectoryPermission(context)
      query <- IO.blocking(resolveQuery(context))
      clubs <- IO.blocking(publicClubs(context))
      playersById <- IO.blocking(publicClubPlayersById(context, clubs))
      relatedClubsById <- IO.blocking(publicRelatedClubsById(context, clubs))
      entries <- IO.blocking(publicClubDirectoryEntries(clubs, playersById, relatedClubsById))
      filteredEntries <- IO.blocking(filterPublicClubDirectoryEntries(context, entries, query))
    yield PagedResponse.fromItems(filteredEntries, limit, offset, query.appliedFilters)(identity)

  private def requirePublicDirectoryPermission(context: ApiPlanContext): IO[Unit] =
    val guest = AccessPrincipalFunctions.guest()
    AuthCheckPermissionAPIMessage(
      principal = Some(guest),
      permission = Permission.ViewClubDirectory
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${guest.displayName} is not allowed to view club directory"))
    }

  private def resolveQuery(context: ApiPlanContext): ResolvedClubDirectoryQuery =
    ResolvedClubDirectoryQuery(
      name = name.filter(_.nonEmpty),
      relation = relation,
      appliedFilters = Vector(
        name.filter(_.nonEmpty).map("name" -> _),
        relation.map(value => "relation" -> ClubRelationKind.toString(value))
      ).flatten.toMap
    )

  private def publicClubs(context: ApiPlanContext): Vector[Club] =
    ClubTable.findFiltered(context.connection, activeOnly = true).sortBy(_.name)

  private def publicClubPlayersById(
      context: ApiPlanContext,
      clubs: Vector[Club]
  ): Map[PlayerId, Player] =
    PlayerPersistenceFunctions.findPlayersByIds(context.connection, clubs.flatMap(_.members).distinct)
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
      publicClubDirectoryEntry(
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
        relations = club.relations.map(ClubRelationView.fromDomain)
      )
    }

  private def publicClubDirectoryEntry(
      clubId: ClubId,
      name: String,
      memberCount: Int,
      activeMemberCount: Int,
      adminCount: Int,
      powerRating: Double,
      totalPoints: Int,
      treasuryBalance: Long,
      pointPool: Int,
      allianceCount: Int,
      rivalryCount: Int,
      strongestRivalClubId: Option[ClubId],
      strongestRivalPower: Option[Double],
      honorTitles: Vector[String],
      relations: Vector[ClubRelationView]
  ): PublicClubDirectoryEntry =
    PublicClubDirectoryEntry(
      clubId = clubId.value,
      name = name,
      memberCount = memberCount,
      activeMemberCount = activeMemberCount,
      adminCount = adminCount,
      powerRating = powerRating,
      totalPoints = totalPoints,
      treasuryBalance = treasuryBalance,
      pointPool = pointPool,
      allianceCount = allianceCount,
      rivalryCount = rivalryCount,
      strongestRivalClubId = strongestRivalClubId.map(_.value),
      strongestRivalPower = strongestRivalPower,
      honorTitles = honorTitles,
      relations = relations
    )

  private def filterPublicClubDirectoryEntries(
      context: ApiPlanContext,
      entries: Vector[PublicClubDirectoryEntry],
      query: ResolvedClubDirectoryQuery
  ): Vector[PublicClubDirectoryEntry] =
    entries
      .filter(club => query.name.forall(riichinexus.system.TextSearch.containsIgnoreCase(club.name, _)))
      .filter(club => query.relation.forall(relationKind => club.relations.exists(_.relation == relationKind)))
      .sortBy(_.name)

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  private final case class ResolvedClubDirectoryQuery(
      name: Option[String],
      relation: Option[ClubRelationKind],
      appliedFilters: Map[String, String]
  )
