package riichinexus.system.objects.`private`

/** 结构化事件 payload Map 中使用的字段名。
  *
  * 这个枚举约束后端写入审计 `details` 与通知 `objects` 时使用的 key，避免各个
  * API 文件中散落 `"playerId"`、`"stageId"`、`"delta"` 这类裸字符串；它不改变
  * 事件 payload 仍使用 `Map[String, String]` 的数据形态。
  */
private[riichinexus] enum StructuredEventField:
  case ApplicationsOpen
  case AssigneeId
  case AttachmentCount
  case AttachmentMediaKinds
  case AttachmentStorageKinds
  case ChampionId
  case ClubId
  case ClubShareRatio
  case Contribution
  case Decision
  case Delta
  case DeviceFingerprint
  case DisplayName
  case DueAt
  case ExpectedReviewSlaHours
  case ExpiresAt
  case HouseFeeAmount
  case LineupSubmissionId
  case MemberCount
  case MembershipId
  case NetPrizePool
  case Note
  case OperatorId
  case PlayerId
  case PointPool
  case Priority
  case PrizePool
  case RankCode
  case RankCount
  case Reason
  case Relation
  case ReopenCount
  case RequirementsText
  case Reserve
  case Revision
  case SettlementId
  case SourceClubId
  case SourceClubName
  case StageId
  case StageName
  case Status
  case TableId
  case TableNo
  case TableResolution
  case TargetClubId
  case TargetClubName
  case Title
  case TournamentId
  case TournamentName
  case TreasuryBalance

object StructuredEventField:
  def toString(field: StructuredEventField): String =
    field match
      case ApplicationsOpen       => "applicationsOpen"
      case AssigneeId             => "assigneeId"
      case AttachmentCount        => "attachmentCount"
      case AttachmentMediaKinds   => "attachmentMediaKinds"
      case AttachmentStorageKinds => "attachmentStorageKinds"
      case ChampionId             => "championId"
      case ClubId                 => "clubId"
      case ClubShareRatio         => "clubShareRatio"
      case Contribution           => "contribution"
      case Decision               => "decision"
      case Delta                  => "delta"
      case DeviceFingerprint      => "deviceFingerprint"
      case DisplayName            => "displayName"
      case DueAt                  => "dueAt"
      case ExpectedReviewSlaHours => "expectedReviewSlaHours"
      case ExpiresAt              => "expiresAt"
      case HouseFeeAmount         => "houseFeeAmount"
      case LineupSubmissionId     => "lineupSubmissionId"
      case MemberCount            => "memberCount"
      case MembershipId           => "membershipId"
      case NetPrizePool           => "netPrizePool"
      case Note                   => "note"
      case OperatorId             => "operatorId"
      case PlayerId               => "playerId"
      case PointPool              => "pointPool"
      case Priority               => "priority"
      case PrizePool              => "prizePool"
      case RankCode               => "rankCode"
      case RankCount              => "rankCount"
      case Reason                 => "reason"
      case Relation               => "relation"
      case ReopenCount            => "reopenCount"
      case RequirementsText       => "requirementsText"
      case Reserve                => "reserve"
      case Revision               => "revision"
      case SettlementId           => "settlementId"
      case SourceClubId           => "sourceClubId"
      case SourceClubName         => "sourceClubName"
      case StageId                => "stageId"
      case StageName              => "stageName"
      case Status                 => "status"
      case TableId                => "tableId"
      case TableNo                => "tableNo"
      case TableResolution        => "tableResolution"
      case TargetClubId           => "targetClubId"
      case TargetClubName         => "targetClubName"
      case Title                  => "title"
      case TournamentId           => "tournamentId"
      case TournamentName         => "tournamentName"
      case TreasuryBalance        => "treasuryBalance"

  def fromString(value: String): StructuredEventField =
    value.trim match
      case "applicationsOpen"       => ApplicationsOpen
      case "assigneeId"             => AssigneeId
      case "attachmentCount"        => AttachmentCount
      case "attachmentMediaKinds"   => AttachmentMediaKinds
      case "attachmentStorageKinds" => AttachmentStorageKinds
      case "championId"             => ChampionId
      case "clubId"                 => ClubId
      case "clubShareRatio"         => ClubShareRatio
      case "contribution"           => Contribution
      case "decision"               => Decision
      case "delta"                  => Delta
      case "deviceFingerprint"      => DeviceFingerprint
      case "displayName"            => DisplayName
      case "dueAt"                  => DueAt
      case "expectedReviewSlaHours" => ExpectedReviewSlaHours
      case "expiresAt"              => ExpiresAt
      case "houseFeeAmount"         => HouseFeeAmount
      case "lineupSubmissionId"     => LineupSubmissionId
      case "memberCount"            => MemberCount
      case "membershipId"           => MembershipId
      case "netPrizePool"           => NetPrizePool
      case "note"                   => Note
      case "operatorId"             => OperatorId
      case "playerId"               => PlayerId
      case "pointPool"              => PointPool
      case "priority"               => Priority
      case "prizePool"              => PrizePool
      case "rankCode"               => RankCode
      case "rankCount"              => RankCount
      case "reason"                 => Reason
      case "relation"               => Relation
      case "reopenCount"            => ReopenCount
      case "requirementsText"       => RequirementsText
      case "reserve"                => Reserve
      case "revision"               => Revision
      case "settlementId"           => SettlementId
      case "sourceClubId"           => SourceClubId
      case "sourceClubName"         => SourceClubName
      case "stageId"                => StageId
      case "stageName"              => StageName
      case "status"                 => Status
      case "tableId"                => TableId
      case "tableNo"                => TableNo
      case "tableResolution"        => TableResolution
      case "targetClubId"           => TargetClubId
      case "targetClubName"         => TargetClubName
      case "title"                  => Title
      case "tournamentId"           => TournamentId
      case "tournamentName"         => TournamentName
      case "treasuryBalance"        => TreasuryBalance
      case other                    => throw IllegalArgumentException(s"Unsupported StructuredEventField value: $other")
