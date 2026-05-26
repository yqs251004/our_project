package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentTableSeatView(
    seat: String,
    playerId: String,
    initialPoints: Int,
    disconnected: Boolean,
    ready: Boolean,
    clubId: Option[String]
) derives CanEqual

object TournamentTableSeatView:
  def fromDomain(seat: riichinexus.domain.model.TableSeat): TournamentTableSeatView =
    TournamentTableSeatView(
      seat = seat.seat.toString,
      playerId = seat.playerId.value,
      initialPoints = seat.initialPoints,
      disconnected = seat.disconnected,
      ready = seat.ready,
      clubId = seat.clubId.map(_.value)
    )

  given ReadWriter[TournamentTableSeatView] = macroRW

