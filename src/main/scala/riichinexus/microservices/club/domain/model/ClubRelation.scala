package riichinexus.microservices.club.domain.model

import java.time.Instant

import riichinexus.domain.model.ClubId

enum ClubRelationKind derives CanEqual:
  case Alliance
  case Rivalry
  case Neutral

final case class ClubRelation(
    targetClubId: ClubId,
    relation: ClubRelationKind,
    updatedAt: Instant,
    note: Option[String] = None
) derives CanEqual
