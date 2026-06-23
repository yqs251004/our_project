package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.objects.{PlayerProfileView, PlayerRoleFlagsView}
import riichinexus.microservices.player.objects.apiTypes.PlayerListQuery
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
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
      query <- IO.blocking(playerListQuery)
      appliedFilters = playerListFilters
      players <- IO.blocking(listPlayers(context, query))
    yield PagedResponse.fromItems(players, limit, offset, appliedFilters)(
      playerProfileView
    )

  private def playerListQuery: PlayerListQuery =
    PlayerListQuery(
      clubId = clubId.filter(_.nonEmpty).map(ClubId(_)),
      status = status.filter(_.nonEmpty).map(
        riichinexus.system.EnumParsing.parse(QueryFilterField.toString(QueryFilterField.Status), _)(PlayerStatus.valueOf)
      ),
      nickname = nickname.filter(_.nonEmpty)
    )

  private def playerListFilters: Map[String, String] =
    Vector(
      clubId.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.ClubId) -> _),
      status.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.Status) -> _),
      nickname.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.Nickname) -> _)
    ).flatten.toMap

  private def listPlayers(
      context: ApiPlanContext,
      query: PlayerListQuery
  ): Vector[Player] =
    PlayerTable.list(context.connection, query.clubId, query.status)
      .filter(player => query.nickname.forall(riichinexus.system.TextSearch.containsIgnoreCase(player.nickname, _)))

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
