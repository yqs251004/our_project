package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerRoleFlagsView}
import riichinexus.microservices.player.objects.apiTypes.PlayerListQuery
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
/** 列出玩家档案。 */
final case class ListPlayersAPIMessage(
    clubId: Option[String] = None,
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerProfileView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerProfileView]] =
    for
      query <- IO.blocking(resolveQuery(context))
      players <- IO.blocking(listPlayers(context, query))
    yield PagedResponse.fromItems(players, limit, offset, query.appliedFilters)(
      playerProfileView
    )

  private def resolveQuery(context: ApiPlanContext): ResolvedPlayersQuery =
    val playerQuery = PlayerListQuery(
      clubId = clubId.filter(_.nonEmpty).map(ClubId(_)),
      status = status.filter(_.nonEmpty).map(riichinexus.system.EnumParsing.parse("status", _)(PlayerStatus.valueOf)),
      nickname = nickname.filter(_.nonEmpty)
    )
    ResolvedPlayersQuery(
      query = playerQuery,
      appliedFilters = Vector(
        clubId.filter(_.nonEmpty).map("clubId" -> _),
        status.filter(_.nonEmpty).map("status" -> _),
        nickname.filter(_.nonEmpty).map("nickname" -> _)
      ).flatten.toMap
    )

  private def listPlayers(
      context: ApiPlanContext,
      resolved: ResolvedPlayersQuery
  ): Vector[Player] =
    PlayerTable.list(context.connection, resolved.query)
      .filter(player => resolved.query.nickname.forall(riichinexus.system.TextSearch.containsIgnoreCase(player.nickname, _)))

  private def playerProfileView(player: Player): PlayerProfileView =
    PlayerProfileView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      registeredAt = player.registeredAt.toString,
      currentRank = player.currentRank,
      elo = player.elo,
      clubId = player.clubId.map(_.value),
      affiliatedClubIds = player.affiliatedClubIds.map(_.value),
      status = player.status.toString,
      roles = PlayerRoleFlagsView(
        isRegisteredPlayer = PlayerRoleFunctions.effectiveRoleGrants(player).exists(_.role == Role.RegisteredPlayer),
        isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
        isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
        isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
      ),
      bannedReason = player.bannedReason
    )

  private final case class ResolvedPlayersQuery(
      query: PlayerListQuery,
      appliedFilters: Map[String, String]
  )
