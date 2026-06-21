package riichinexus.microservices.tournament.objects.stage.table

/** 四人麻将牌桌中的座位风。
  *
  * 座位风用于排桌、牌谱记录、对局结果和前端牌桌布局，`all` 保持东南西北的固定座位顺序。
  */
enum SeatWind:
  case East
  case South
  case West
  case North

object SeatWind:

  val all: Vector[SeatWind] = Vector(East, South, West, North)

  def toString(wind: SeatWind): String =
    wind match
      case SeatWind.East => "East"
      case SeatWind.South => "South"
      case SeatWind.West => "West"
      case SeatWind.North => "North"

  def fromString(value: String): Either[String, SeatWind] =
    value.trim match
      case "East" => Right(SeatWind.East)
      case "South" => Right(SeatWind.South)
      case "West" => Right(SeatWind.West)
      case "North" => Right(SeatWind.North)
      case other => Left(s"Unsupported SeatWind value: $other")
