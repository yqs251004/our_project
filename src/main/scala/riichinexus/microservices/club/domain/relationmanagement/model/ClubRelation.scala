package riichinexus.microservices.club.domain.relationmanagement.model

import java.time.Instant

import riichinexus.domain.model.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind

final case class ClubRelation(
    targetClubId: ClubId,
    relation: ClubRelationKind,
    updatedAt: Instant,
    note: Option[String] = None
) derives CanEqual
