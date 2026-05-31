package riichinexus.microservices.tournament.domain.model

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.{SeatWind}

final case class TableSeat(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int = 25000,
    disconnected: Boolean = false,
    ready: Boolean = false,
    clubId: Option[ClubId] = None
) derives CanEqual:
  require(initialPoints > 0, "Seat initial points must be positive")

  def markReady: TableSeat =
    require(!disconnected, "Disconnected seats cannot be marked ready")
    copy(ready = true)

  def markNotReady: TableSeat =
    copy(ready = false)

  def markDisconnected: TableSeat =
    copy(disconnected = true, ready = false)

  def markConnected: TableSeat =
    copy(disconnected = false)

