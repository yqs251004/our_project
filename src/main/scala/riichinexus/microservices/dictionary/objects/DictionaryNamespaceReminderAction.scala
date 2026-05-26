package riichinexus.microservices.dictionary.objects

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId}

final case class DictionaryNamespaceReminderAction(
    namespacePrefix: String,
    contextClubId: Option[ClubId],
    ownerPlayerId: PlayerId,
    coOwnerPlayerIds: Vector[PlayerId],
    editorPlayerIds: Vector[PlayerId],
    reminderKind: DictionaryNamespaceReminderKind,
    triggeredAt: Instant,
    dueAt: Option[Instant],
    reminderCount: Int
) derives CanEqual
