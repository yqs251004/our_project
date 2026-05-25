package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.{ClubMemberPrivilegeSnapshot as ClubMemberPrivilegeSnapshotResponse}
import upickle.default.*

final case class GetClubMemberPrivilegeAPIMessage(
    clubId: String,
    playerId: String
) extends APIMessage[ClubMemberPrivilegeSnapshotResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMemberPrivilegeSnapshotResponse] =
    IO {
      context.support.clubModule.tables.memberPrivilegeSnapshot(ClubId(clubId), PlayerId(playerId))
        .map(ClubMemberPrivilegeSnapshotResponse.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
