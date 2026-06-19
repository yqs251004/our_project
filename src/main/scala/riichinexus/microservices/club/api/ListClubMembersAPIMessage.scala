package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.objects.apiTypes.PlayerProfileView
import riichinexus.microservices.player.api.ListPlayersAPIMessage
import riichinexus.system.objects.PagedResponse
/** 列出俱乐部成员。 */
final case class ListClubMembersAPIMessage(
    clubId: String,
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerProfileView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerProfileView]] =
    for
      query <- IO.pure(resolveQuery)
      members <- ListPlayersAPIMessage(
        clubId = Some(query.clubId.value),
        status = query.status.map(_.toString),
        nickname = query.nickname,
        limit = Some(query.limit),
        offset = Some(query.offset)
      ).plan(context)
    yield members

  private def resolveQuery: ResolvedClubMembersQuery =
    ResolvedClubMembersQuery(
      clubId = ClubId(clubId),
      status = status.filter(_.nonEmpty).map(riichinexus.system.EnumParsing.parse("status", _)(PlayerStatus.valueOf)),
      nickname = nickname.filter(_.nonEmpty),
      limit = limit.getOrElse(20),
      offset = offset.getOrElse(0),
      appliedFilters = Vector(
        status.filter(_.nonEmpty).map("status" -> _),
        nickname.filter(_.nonEmpty).map("nickname" -> _)
      ).flatten.toMap
    )

  private final case class ResolvedClubMembersQuery(
      clubId: ClubId,
      status: Option[PlayerStatus],
      nickname: Option[String],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
