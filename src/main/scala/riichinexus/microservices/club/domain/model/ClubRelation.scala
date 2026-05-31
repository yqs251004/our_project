package riichinexus.microservices.club.domain.model

import java.time.Instant

import riichinexus.domain.model.ClubId
import riichinexus.microservices.club.objects.ClubRelationKind

final case class ClubRelation(
    targetClubId: ClubId,
    relation: ClubRelationKind,
    updatedAt: Instant,
    note: Option[String] = None
) derives CanEqual
