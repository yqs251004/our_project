package riichinexus.domain.service

import riichinexus.domain.model.*

final case class PlannedTable(
    tableNo: Int,
    seats: Vector[TableSeat]
) derives CanEqual

trait SeatingPolicy:
  def assignTables(players: Vector[Player], stage: TournamentStage): Vector[PlannedTable]

final class BalancedEloSeatingPolicy extends SeatingPolicy:
  override def assignTables(players: Vector[Player], stage: TournamentStage): Vector[PlannedTable] =
    require(players.nonEmpty, s"Stage ${stage.name} needs players before scheduling")
    require(players.size % 4 == 0, "Player count must be divisible by 4 to schedule riichi tables")

    players
      .sortBy(player =>
        (
          -player.elo,
          player.clubId.map(_.value).getOrElse("zzzzzz"),
          player.nickname
        )
      )
      .grouped(4)
      .zipWithIndex
      .map { case (group, index) =>
        val rotatedGroup = rotate(group.toVector, index % group.size)
        PlannedTable(
          tableNo = index + 1,
          seats = SeatWind.all.zip(rotatedGroup).map { case (seat, player) =>
            TableSeat(seat, player.id, clubId = player.clubId)
          }
        )
      }
      .toVector

  private def rotate[A](values: Vector[A], shift: Int): Vector[A] =
    if values.isEmpty then values
    else
      val normalized = shift % values.size
      values.drop(normalized) ++ values.take(normalized)
