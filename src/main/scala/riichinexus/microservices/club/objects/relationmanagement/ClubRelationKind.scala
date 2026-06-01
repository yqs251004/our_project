package riichinexus.microservices.club.objects.relationmanagement

import upickle.default.*

enum ClubRelationKind derives CanEqual:
  case Alliance
  case Rivalry
  case Neutral

object ClubRelationKind:
  given ReadWriter[ClubRelationKind] =
    readwriter[String].bimap(toString, fromString)

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
