package riichinexus.microservices.club.objects

import riichinexus.microservices.club.domain.model.{ClubRelationKind as DomainClubRelationKind}
import upickle.default.*

enum ClubRelationKind derives CanEqual:
  case Alliance
  case Rivalry
  case Neutral

  def toDomain: DomainClubRelationKind =
    DomainClubRelationKind.valueOf(toString)

object ClubRelationKind:
  given ReadWriter[ClubRelationKind] = readwriter[String].bimap(_.toString, ClubRelationKind.valueOf)

  def fromDomain(relationKind: DomainClubRelationKind): ClubRelationKind =
    ClubRelationKind.valueOf(relationKind.toString)
