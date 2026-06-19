package riichinexus.microservices.tournament.domain.stage.functions.scheduling


import riichinexus.microservices.tournament.objects.stage.table.TableSeat

/** TableSeatFunctions 提供牌桌座位相关的领域计算、校验和转换函数。 */

private[tournament] object TableSeatFunctions:
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
