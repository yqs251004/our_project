package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import upickle.default.*

final case class ListPlayerPaifusPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[Vector[Paifu]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Paifu]] =
    for
      paifus <- IO.blocking(PaifuTable.findByPlayer(context.connection, playerId))
    yield paifus
