package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{TableSeat as DomainTableSeat}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TableSeat(
    seat: SeatWind,
    playerId: String,
    initialPoints: Int,
    disconnected: Boolean,
    ready: Boolean,
    clubId: Option[String]
) derives ReadWriter

object TableSeat:
  def fromDomain(seat: DomainTableSeat): TableSeat =
    TableSeat(
      seat = seat.seat,
      playerId = seat.playerId.value,
      initialPoints = seat.initialPoints,
      disconnected = seat.disconnected,
      ready = seat.ready,
      clubId = seat.clubId.map(_.value)
    )
