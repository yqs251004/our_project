package riichinexus.microservices.club.objects.relation

/** 俱乐部之间可公开展示和筛选的关系类型。
  *
  * 同盟、宿敌和中立关系会影响公开目录的关系标签与筛选结果，但不直接等同于访问权限。
  */
enum ClubRelationKind:
  case Alliance
  case Rivalry
  case Neutral

object ClubRelationKind:
  def toString(kind: ClubRelationKind): String =
    kind match
      case Alliance => "Alliance"
      case Rivalry  => "Rivalry"
      case Neutral  => "Neutral"

  def fromString(value: String): ClubRelationKind =
    value.trim match
      case "Alliance" => Alliance
      case "Rivalry"  => Rivalry
      case "Neutral"  => Neutral
      case other      => throw IllegalArgumentException(s"Unsupported ClubRelationKind value: $other")
