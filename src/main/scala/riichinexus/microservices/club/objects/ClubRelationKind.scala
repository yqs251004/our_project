package riichinexus.microservices.club.objects

import upickle.default.*

enum ClubRelationKind derives CanEqual:
  case Alliance
  case Rivalry
  case Neutral

object ClubRelationKind:
  given ReadWriter[ClubRelationKind] = readwriter[String].bimap(_.toString, ClubRelationKind.valueOf)
