package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.recordmanagement.model.MatchRecord
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import upickle.default.*

final case class ListPlayerMatchRecordsPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[Vector[MatchRecord]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[MatchRecord]] =
    for
      records <- IO.blocking(MatchRecordTable.findByPlayer(context.connection, playerId))
    yield records
