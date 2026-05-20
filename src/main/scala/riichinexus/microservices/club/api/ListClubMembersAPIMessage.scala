package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.PlayerResponses.given
import riichinexus.system.objects.apiTypes.PagedResponse
import upickle.default.*

final case class ListClubMembersAPIMessage(
    clubId: String,
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerProfileView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerProfileView]] =
    IO {
      val parsedStatus = status.filter(_.nonEmpty).map(context.support.parseEnum("status", _)(PlayerStatus.valueOf))
      val parsedNickname = nickname.filter(_.nonEmpty)
      val members = context.support.clubModule.tables
        .listPlayersByClub(ClubId(clubId))
        .filter(player => parsedStatus.forall(_ == player.status))
        .filter(player => parsedNickname.forall(context.support.containsIgnoreCase(player.nickname, _)))
        .sortBy(player => (player.nickname, player.id.value))
        .map(PlayerProfileView.fromDomain)
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = members.slice(resolvedOffset, resolvedOffset + boundedLimit)
      PagedResponse(
        items = page,
        total = members.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < members.size,
        appliedFilters = Vector(
          status.filter(_.nonEmpty).map("status" -> _),
          nickname.filter(_.nonEmpty).map("nickname" -> _)
        ).flatten.toMap
      )
    }
