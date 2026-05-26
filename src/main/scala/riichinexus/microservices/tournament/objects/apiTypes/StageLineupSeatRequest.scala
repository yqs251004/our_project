package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.SeatWind
import upickle.default.*

final case class StageLineupSeatRequest(
    playerId: String,
    preferredWind: Option[String] = None,
    reserve: Boolean = false
):
  def toSeat: StageLineupSeat =
    StageLineupSeat(
      playerId = PlayerId(playerId),
      preferredWind = preferredWind.map(SeatWind.valueOf),
      reserve = reserve
    )

object StageLineupSeatRequest:
  given ReadWriter[StageLineupSeatRequest] = macroRW
