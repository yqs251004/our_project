package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class StageTablePlan(
    roundNumber: Int,
    tableNo: Int,
    seats: Vector[TableSeat]
) derives CanEqual:
  require(roundNumber >= 1, "Stage table plan round number must be positive")
  require(seats.size == 4, "Stage table plan must contain four seats")

