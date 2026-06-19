package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.auth.domain.model.RoleGrant
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class GrantPlayerRolePrivateAPIMessage(
    playerId: PlayerId,
    grant: RoleGrant
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerPrivateMutationSupport.updatePlayer(context, playerId)(
      PlayerRoleFunctions.grantRole(_, grant)
    )
