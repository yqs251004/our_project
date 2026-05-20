package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryRequestNamespaceAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    ownerPlayerId: Option[String] = None,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None,
    reviewDueAt: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistration] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistration] =
    IO {
      val module = context.support.dictionaryModule
      val request = RequestDictionaryNamespaceRequest(operatorId, namespacePrefix, contextClubId, ownerPlayerId, coOwnerPlayerIds, editorPlayerIds, note, reviewDueAt)
      val actor = context.support.principal(request.operator)
      val requestedAt = Instant.now()

      module.transactionManager.inTransaction {
        val requesterId = actor.playerId.getOrElse(
          throw IllegalArgumentException("Dictionary namespace requests require a registered player identity")
        )
        val effectiveOwner = request.owner.getOrElse(requesterId)
        if effectiveOwner != requesterId && !actor.isSuperAdmin then
          throw IllegalArgumentException("Only super admins can request a namespace on behalf of another owner")

        val owner = requireActiveNamespaceOwner(context, effectiveOwner, s"request ownership for ${effectiveOwner.value}")
        val (normalizedCoOwners, normalizedEditors) = normalizeNamespaceCollaborators(
          context,
          effectiveOwner,
          request.coOwners,
          request.editors,
          s"request ${request.namespacePrefix.trim}"
        )
        val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(request.namespacePrefix)
        val effectiveReviewDueAt = request.parsedReviewDueAt.orElse(Some(requestedAt.plus(java.time.Duration.ofHours(72))))
        require(
          effectiveReviewDueAt.forall(!_.isBefore(requestedAt)),
          "Dictionary namespace reviewDueAt cannot be earlier than requestedAt"
        )
        val normalizedContextClubId = validateNamespaceContextMembership(
          context,
          request.contextClub,
          owner,
          normalizedCoOwners,
          normalizedEditors,
          s"request ${normalizedPrefix.trim}"
        )

        module.tables.findNamespaceByPrefix(normalizedPrefix) match
          case Some(existing)
              if existing.status == DictionaryNamespaceReviewStatus.Approved &&
                existing.ownerPlayerId == effectiveOwner &&
                existing.coOwnerPlayerIds == normalizedCoOwners &&
                existing.editorPlayerIds == normalizedEditors &&
                existing.contextClubId == normalizedContextClubId =>
            existing
          case Some(existing) if existing.status == DictionaryNamespaceReviewStatus.Approved =>
            throw IllegalArgumentException(s"Dictionary namespace $normalizedPrefix is already owned by ${existing.ownerPlayerId.value}")
          case Some(existing) if existing.status == DictionaryNamespaceReviewStatus.Pending =>
            throw IllegalArgumentException(s"Dictionary namespace $normalizedPrefix already has a pending review")
          case _ =>
            val registration = module.dictionaryNamespaceRepository.save(
              DictionaryNamespaceRegistration(
                namespacePrefix = normalizedPrefix,
                contextClubId = normalizedContextClubId,
                ownerPlayerId = effectiveOwner,
                coOwnerPlayerIds = normalizedCoOwners,
                editorPlayerIds = normalizedEditors,
                requestedBy = requesterId,
                requestedAt = requestedAt,
                reviewDueAt = effectiveReviewDueAt,
                reviewNote = request.note
              )
            )
            module.auditEventRepository.save(
              AuditEventEntry(
                id = IdGenerator.auditEventId(),
                aggregateType = "dictionary-namespace",
                aggregateId = normalizedPrefix,
                eventType = "DictionaryNamespaceRequested",
                occurredAt = requestedAt,
                actorId = Some(requesterId),
                details = Map(
                  "contextClubId" -> normalizedContextClubId.map(_.value).getOrElse(""),
                  "ownerPlayerId" -> effectiveOwner.value,
                  "coOwnerPlayerIds" -> normalizedCoOwners.map(_.value).mkString(","),
                  "editorPlayerIds" -> normalizedEditors.map(_.value).mkString(","),
                  "reviewDueAt" -> effectiveReviewDueAt.map(_.toString).getOrElse("")
                ),
                note = request.note
              )
            )
            registration
      }
    }

  private def requireActiveNamespaceOwner(context: ApiPlanContext, playerId: PlayerId, action: String): Player =
    context.support.dictionaryModule.playerRepository.findById(playerId) match
      case Some(player) if player.status == PlayerStatus.Active => player
      case Some(player) =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an active player owner, but ${playerId.value} is ${player.status.toString.toLowerCase}"
        )
      case None =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing player owner, but ${playerId.value} was not found"
        )

  private def validateNamespaceContextMembership(
      context: ApiPlanContext,
      contextClubId: Option[ClubId],
      owner: Player,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): Option[ClubId] =
    contextClubId.map { clubId =>
      context.support.dictionaryModule.clubRepository.findById(clubId).getOrElse(
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing context club, but ${clubId.value} was not found"
        )
      )
      requireNamespaceContextMembership(owner, clubId, s"$action owner ${owner.id.value}")
      coOwnerPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(context, playerId, s"$action co-owner ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$action co-owner ${playerId.value}")
      }
      editorPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(context, playerId, s"$action editor ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$action editor ${playerId.value}")
      }
      clubId
    }

  private def requireNamespaceContextMembership(player: Player, contextClubId: ClubId, action: String): Unit =
    if !player.boundClubIds.contains(contextClubId) then
      throw IllegalArgumentException(
        s"Dictionary namespace $action requires ${player.id.value} to belong to context club ${contextClubId.value}"
      )

  private def normalizeNamespaceCollaborators(
      context: ApiPlanContext,
      ownerPlayerId: PlayerId,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): (Vector[PlayerId], Vector[PlayerId]) =
    val normalizedCoOwners = coOwnerPlayerIds.distinct.filterNot(_ == ownerPlayerId)
    normalizedCoOwners.foreach(playerId => requireActiveNamespaceOwner(context, playerId, s"$action co-owner ${playerId.value}"))
    val normalizedEditors =
      editorPlayerIds.distinct.filterNot(playerId => playerId == ownerPlayerId || normalizedCoOwners.contains(playerId))
    normalizedEditors.foreach(playerId => requireActiveNamespaceOwner(context, playerId, s"$action editor ${playerId.value}"))
    (normalizedCoOwners, normalizedEditors)
