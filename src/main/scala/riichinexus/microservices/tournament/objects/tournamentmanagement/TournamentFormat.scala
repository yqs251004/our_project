package riichinexus.microservices.tournament.objects.tournamentmanagement

/** TournamentFormat 枚举赛事Format 可使用的公开取值。 */

enum TournamentFormat:
  case Swiss
  case Knockout
  case RoundRobin
  case Finals
  case Custom

object TournamentFormat:

  def toString(format: TournamentFormat): String =
    format match
      case TournamentFormat.Swiss => "Swiss"
      case TournamentFormat.Knockout => "Knockout"
      case TournamentFormat.RoundRobin => "RoundRobin"
      case TournamentFormat.Finals => "Finals"
      case TournamentFormat.Custom => "Custom"

  def fromString(value: String): Either[String, TournamentFormat] =
    value.trim match
      case "Swiss" => Right(TournamentFormat.Swiss)
      case "Knockout" => Right(TournamentFormat.Knockout)
      case "RoundRobin" => Right(TournamentFormat.RoundRobin)
      case "Finals" => Right(TournamentFormat.Finals)
      case "Custom" => Right(TournamentFormat.Custom)
      case other => Left(s"Unsupported TournamentFormat value: $other")
