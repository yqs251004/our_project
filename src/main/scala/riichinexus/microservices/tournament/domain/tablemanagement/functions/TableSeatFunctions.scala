package riichinexus.microservices.tournament.domain.tablemanagement.functions

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.microservices.tournament.objects.tablemanagement.TableSeat

object TableSeatFunctions:
  def validate(seat: TableSeat): TableSeat =
    require(seat.initialPoints > 0, "Seat initial points must be positive")
    seat

  def markReady(seat: TableSeat): TableSeat =
    validate(seat)
    require(!seat.disconnected, "Disconnected seats cannot be marked ready")
    seat.copy(ready = true)

  def markNotReady(seat: TableSeat): TableSeat =
    validate(seat)
    seat.copy(ready = false)

  def markDisconnected(seat: TableSeat): TableSeat =
    validate(seat)
    seat.copy(disconnected = true, ready = false)

  def markConnected(seat: TableSeat): TableSeat =
    validate(seat)
    seat.copy(disconnected = false)
