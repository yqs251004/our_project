package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.objects.{Player, PlayerStatus}
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerRoleFlagsView}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.RankSnapshotView
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubMembersAPIMessage(
    clubId: String,
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerProfileView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerProfileView]] =
    for
      query <- IO.blocking(resolveQuery(context))
      members <- IO.blocking(listMembers(context, query))
    yield pagedResponse(members, query)

  private def resolveQuery(context: ApiPlanContext): ResolvedClubMembersQuery =
    ResolvedClubMembersQuery(
      clubId = ClubId(clubId),
      status = status.filter(_.nonEmpty).map(context.support.parseEnum("status", _)(PlayerStatus.valueOf)),
      nickname = nickname.filter(_.nonEmpty),
      limit = limit.getOrElse(20),
      offset = offset.getOrElse(0),
      appliedFilters = Vector(
        status.filter(_.nonEmpty).map("status" -> _),
        nickname.filter(_.nonEmpty).map("nickname" -> _)
      ).flatten.toMap
    )

  private def listMembers(
      context: ApiPlanContext,
      query: ResolvedClubMembersQuery
  ): Vector[PlayerProfileView] =
    ListPlayersAPIMessage
      .findPlayersByClub(context.connection, query.clubId)
      .filter(player => query.status.forall(_ == player.status))
      .filter(player => query.nickname.forall(context.support.containsIgnoreCase(player.nickname, _)))
      .sortBy(player => (player.nickname, player.id.value))
      .map(playerProfileView)

  private def playerProfileView(player: Player): PlayerProfileView =
    PlayerProfileView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      registeredAt = player.registeredAt.toString,
      currentRank = RankSnapshotView(player.currentRank.platform, player.currentRank.tier, player.currentRank.stars),
      elo = player.elo,
      clubId = player.clubId.map(_.value),
      affiliatedClubIds = player.affiliatedClubIds.map(_.value),
      status = player.status.toString,
      roles = PlayerRoleFlagsView(
        isRegisteredPlayer = player.effectiveRoleGrants.exists(_.role == Role.RegisteredPlayer),
        isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
        isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
        isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
      ),
      bannedReason = player.bannedReason
    )

  private def pagedResponse(
      members: Vector[PlayerProfileView],
      query: ResolvedClubMembersQuery
  ): PagedResponse[PlayerProfileView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val page = members.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(
      items = page,
      total = members.size,
      limit = boundedLimit,
      offset = query.offset,
      hasMore = query.offset + page.size < members.size,
      appliedFilters = query.appliedFilters
    )

  private final case class ResolvedClubMembersQuery(
      clubId: ClubId,
      status: Option[PlayerStatus],
      nickname: Option[String],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
