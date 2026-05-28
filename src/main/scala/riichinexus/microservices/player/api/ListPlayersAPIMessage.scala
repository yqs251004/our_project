package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.{Player, PlayerProfileView, PlayerStatus}
import riichinexus.microservices.player.objects.apiTypes.PlayerListQuery
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListPlayersAPIMessage(
    clubId: Option[String] = None,
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerProfileView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerProfileView]] =
    for
      query <- IO.blocking(resolveQuery(context))
      players <- IO.blocking(listPlayers(context, query))
    yield PagedResponse.fromItems(players, limit, offset, query.appliedFilters)(
      PlayerProfileView.fromDomain
    )

  private def resolveQuery(context: ApiPlanContext): ResolvedPlayersQuery =
    val playerQuery = PlayerListQuery(
      clubId = clubId.filter(_.nonEmpty).map(ClubId(_)),
      status = status.filter(_.nonEmpty).map(context.support.parseEnum("status", _)(PlayerStatus.valueOf)),
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
      .filter(player => resolved.query.nickname.forall(context.support.containsIgnoreCase(player.nickname, _)))

  private final case class ResolvedPlayersQuery(
      query: PlayerListQuery,
      appliedFilters: Map[String, String]
  )
