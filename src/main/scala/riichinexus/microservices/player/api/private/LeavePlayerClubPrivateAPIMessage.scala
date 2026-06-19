package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.{PlayerClubBindingFunctions, PlayerRoleFunctions}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class LeavePlayerClubPrivateAPIMessage(
    playerId: PlayerId,
    clubId: ClubId,
    revokeClubAdmin: Boolean = true
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerPrivateMutationSupport.updatePlayer(context, playerId) { player =>
      val unbound = PlayerClubBindingFunctions.leaveClub(player, clubId)
      if revokeClubAdmin then PlayerRoleFunctions.revokeClubAdmin(unbound, clubId)
      else unbound
    }
