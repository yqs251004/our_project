package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import java.time.Instant
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class DissolveClubPrivateAPIMessage(
    clubId: ClubId,
    dissolvedBy: PlayerId,
    dissolvedAt: Instant
) extends APIMessage[Option[Club]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    ClubPrivateMutationSupport.updateClub(context, clubId)(
      ClubFunctions.dissolve(_, dissolvedBy, dissolvedAt)
    )
