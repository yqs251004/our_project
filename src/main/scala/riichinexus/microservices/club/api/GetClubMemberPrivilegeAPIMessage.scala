package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class GetClubMemberPrivilegeAPIMessage(
    clubId: String,
    playerId: String
) extends APIMessage[ClubMemberPrivilegeSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMemberPrivilegeSnapshot] =
    IO {
      context.support.clubModule.tables.memberPrivilegeSnapshot(ClubId(clubId), PlayerId(playerId))
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
