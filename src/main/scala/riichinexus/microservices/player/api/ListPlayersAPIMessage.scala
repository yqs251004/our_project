package riichinexus.microservices.player.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.apiTypes.PlayerListQuery
import riichinexus.microservices.player.objects.apiTypes.PlayerProfileView
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
    IO {
      val query = PlayerListQuery(
        clubId = clubId.filter(_.nonEmpty).map(ClubId(_)),
        status = status.filter(_.nonEmpty).map(context.support.parseEnum("status", _)(PlayerStatus.valueOf)),
        nickname = nickname.filter(_.nonEmpty)
      )
      val players = context.support.playerModule.tables.listPlayers(query)
        .filter(player => query.nickname.forall(context.support.containsIgnoreCase(player.nickname, _)))
        .map(PlayerProfileView.fromDomain)
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = players.slice(resolvedOffset, resolvedOffset + boundedLimit)

      PagedResponse(
        items = page,
        total = players.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < players.size,
        appliedFilters = Vector(
          clubId.filter(_.nonEmpty).map("clubId" -> _),
          status.filter(_.nonEmpty).map("status" -> _),
          nickname.filter(_.nonEmpty).map("nickname" -> _)
        ).flatten.toMap
      )
    }
