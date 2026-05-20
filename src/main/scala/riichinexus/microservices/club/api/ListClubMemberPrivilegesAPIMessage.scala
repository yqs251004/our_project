package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubMemberPrivilegesAPIMessage(
    clubId: String,
    playerId: Option[String] = None,
    privilege: Option[String] = None,
    rankCode: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubMemberPrivilegeSnapshot]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMemberPrivilegeSnapshot]] =
    IO {
      val parsedPlayerId = playerId.filter(_.nonEmpty).map(PlayerId(_))
      val parsedPrivilege = privilege.filter(_.nonEmpty).map(ClubPrivilegeRegistry.requireSupported)
      val parsedRankCode = rankCode.filter(_.nonEmpty).map(_.trim.toLowerCase)
      val snapshots = context.support.clubModule.tables
        .listMemberPrivilegeSnapshots(ClubId(clubId))
        .filter(snapshot => parsedPlayerId.forall(_ == snapshot.playerId))
        .filter(snapshot => parsedPrivilege.forall(snapshot.privileges.contains))
        .filter(snapshot => parsedRankCode.forall(_ == snapshot.rankCode.trim.toLowerCase))
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = snapshots.slice(resolvedOffset, resolvedOffset + boundedLimit)
      PagedResponse(
        items = page,
        total = snapshots.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < snapshots.size,
        appliedFilters = Vector(
          playerId.filter(_.nonEmpty).map("playerId" -> _),
          privilege.filter(_.nonEmpty).map("privilege" -> _),
          rankCode.filter(_.nonEmpty).map("rankCode" -> _)
        ).flatten.toMap
      )
    }
