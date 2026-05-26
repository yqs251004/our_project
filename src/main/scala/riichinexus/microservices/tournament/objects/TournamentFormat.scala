package riichinexus.microservices.tournament.objects

import riichinexus.domain.model.StageFormat

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

  def fromStageFormat(format: StageFormat): TournamentFormat =
    format match
      case StageFormat.Swiss      => TournamentFormat.Swiss
      case StageFormat.Knockout   => TournamentFormat.Knockout
      case StageFormat.RoundRobin => TournamentFormat.RoundRobin
      case StageFormat.Finals     => TournamentFormat.Finals
      case StageFormat.Custom     => TournamentFormat.Custom

  def toStageFormat(format: TournamentFormat): StageFormat =
    format match
      case TournamentFormat.Swiss      => StageFormat.Swiss
      case TournamentFormat.Knockout   => StageFormat.Knockout
      case TournamentFormat.RoundRobin => StageFormat.RoundRobin
      case TournamentFormat.Finals     => StageFormat.Finals
      case TournamentFormat.Custom     => StageFormat.Custom
