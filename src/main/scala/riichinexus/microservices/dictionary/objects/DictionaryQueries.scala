package riichinexus.microservices.dictionary.objects

import java.time.Instant

import riichinexus.domain.model.{ClubId, DictionaryNamespaceReviewStatus, PlayerId}

final case class DictionaryEntryQuery(
    prefix: Option[String] = None,
    updatedBy: Option[PlayerId] = None
)

final case class DictionaryNamespaceBacklogQuery(
    asOf: Instant,
    dueSoonHours: Long = 24L
)

final case class DictionaryNamespaceListQuery(
    status: Option[DictionaryNamespaceReviewStatus] = None,
    contextClubId: Option[ClubId] = None,
    ownerId: Option[PlayerId] = None,
    requestedBy: Option[PlayerId] = None,
    reviewedBy: Option[PlayerId] = None,
    asOf: Instant,
    overdueOnly: Boolean = false,
    dueBefore: Option[Instant] = None,
    dueAfter: Option[Instant] = None
)
