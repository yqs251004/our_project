package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.api.ApiPlanContext

/** PlayerDomainRecord 供后端服务执行玩家Domain记录 流程，避免其它微服务直接访问内部表或领域模型。 */

private[player] object PlayerDomainRecord:
  def find(context: ApiPlanContext, playerId: PlayerId): IO[Option[Player]] =
    IO.blocking(PlayerTable.findById(context.connection, playerId))

  def save(context: ApiPlanContext, player: Player): IO[Player] =
    IO.blocking(PlayerTable.save(context.connection, player))
