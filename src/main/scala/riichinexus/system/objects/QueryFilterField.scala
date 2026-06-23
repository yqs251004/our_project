package riichinexus.system.objects

/** 分页响应 `appliedFilters` 中对外回显的过滤字段名。
  *
  * 这个枚举只约束后端写入 `Map[String, String]` 时使用的 key，避免列表 API 里散落
  * `"stageId"`、`"playerId"` 这类裸字符串；它不表示筛选值本身，也不会改变 API
  * 响应仍然使用字符串 Map 的外部形态。
  */
enum QueryFilterField:
  case ActiveOnly
  case AdminId
  case AsOf
  case AssigneeId
  case ChampionId
  case ClubId
  case DueAfter
  case DueBefore
  case DisplayName
  case JoinableOnly
  case MemberId
  case Name
  case Nickname
  case OpenedBy
  case OperatorId
  case Organizer
  case OverdueOnly
  case ParticipantKind
  case PlayerId
  case Priority
  case Privilege
  case RankCode
  case Relation
  case RoundNumber
  case StageId
  case Status
  case StageStatus
  case TableId
  case TournamentId
  case TournamentStatus

object QueryFilterField:
  def toString(field: QueryFilterField): String =
    field match
      case ActiveOnly      => "activeOnly"
      case AdminId         => "adminId"
      case AsOf            => "asOf"
      case AssigneeId      => "assigneeId"
      case ChampionId      => "championId"
      case ClubId          => "clubId"
      case DueAfter        => "dueAfter"
      case DueBefore       => "dueBefore"
      case DisplayName     => "displayName"
      case JoinableOnly    => "joinableOnly"
      case MemberId        => "memberId"
      case Name            => "name"
      case Nickname        => "nickname"
      case OpenedBy        => "openedBy"
      case OperatorId      => "operatorId"
      case Organizer       => "organizer"
      case OverdueOnly     => "overdueOnly"
      case ParticipantKind => "participantKind"
      case PlayerId        => "playerId"
      case Priority        => "priority"
      case Privilege       => "privilege"
      case RankCode        => "rankCode"
      case Relation        => "relation"
      case RoundNumber     => "roundNumber"
      case StageId         => "stageId"
      case Status          => "status"
      case StageStatus     => "stageStatus"
      case TableId         => "tableId"
      case TournamentId    => "tournamentId"
      case TournamentStatus => "tournamentStatus"

  def fromString(value: String): QueryFilterField =
    value.trim match
      case "activeOnly"      => ActiveOnly
      case "adminId"         => AdminId
      case "asOf"            => AsOf
      case "assigneeId"      => AssigneeId
      case "championId"      => ChampionId
      case "clubId"          => ClubId
      case "dueAfter"        => DueAfter
      case "dueBefore"       => DueBefore
      case "displayName"     => DisplayName
      case "joinableOnly"    => JoinableOnly
      case "memberId"        => MemberId
      case "name"            => Name
      case "nickname"        => Nickname
      case "openedBy"        => OpenedBy
      case "operatorId"      => OperatorId
      case "organizer"       => Organizer
      case "overdueOnly"     => OverdueOnly
      case "participantKind" => ParticipantKind
      case "playerId"        => PlayerId
      case "priority"        => Priority
      case "privilege"       => Privilege
      case "rankCode"        => RankCode
      case "relation"        => Relation
      case "roundNumber"     => RoundNumber
      case "stageId"         => StageId
      case "status"          => Status
      case "stageStatus"     => StageStatus
      case "tableId"         => TableId
      case "tournamentId"    => TournamentId
      case "tournamentStatus" => TournamentStatus
      case other             => throw IllegalArgumentException(s"Unsupported QueryFilterField value: $other")
