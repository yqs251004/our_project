package riichinexus.microservices.tournament.domain

import riichinexus.microservices.tournament.domain.model.TableSeat

final case class PlannedTable(
    tableNo: Int,
    seats: Vector[TableSeat]
) derives CanEqual
