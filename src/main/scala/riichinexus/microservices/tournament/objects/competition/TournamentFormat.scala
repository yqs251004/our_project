package riichinexus.microservices.tournament.objects.competition

/** 赛事或阶段采用的竞赛组织方式。
  *
  * 赛制会影响阶段规划、排桌、晋级和前端规则展示，例如瑞士轮、淘汰赛、循环赛和决赛阶段。
  */
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
