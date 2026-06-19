package riichinexus.microservices.tournament.objects.tablemanagement

/** SeatWind 枚举座位Wind 可使用的公开取值。 */

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
