package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AdjustClubMemberContributionAPIMessage(
    clubId: String,
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val parsedPlayerId = PlayerId(playerId)
      val actor = context.support.principal(PlayerId(operatorId))
      val occurredAt = java.time.Instant.now()

      module.transactionManager.inTransaction {
        (for
          club <- module.clubRepository.findById(parsedClubId)
          player <- module.playerRepository.findById(parsedPlayerId)
        yield
          ensureClubActive(club)
          requireActivePlayer(player, s"Player ${parsedPlayerId.value} cannot receive club contribution updates")
          requireClubMember(club, parsedPlayerId, "adjust contribution")
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageClubOperations,
            clubId = Some(parsedClubId)
          )

          val updatedBy = actor.playerId.getOrElse(club.creator)
          val nextContribution = club.contributionOf(parsedPlayerId) + delta
          require(nextContribution >= 0, s"Club member contribution for ${parsedPlayerId.value} cannot be negative")

          val updatedClub = module.clubRepository.save(
            club.updateMemberContribution(
              ClubMemberContribution(
                playerId = parsedPlayerId,
                amount = nextContribution,
                updatedAt = occurredAt,
                updatedBy = updatedBy,
                note = note
              )
            )
          )
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "club",
              aggregateId = parsedClubId.value,
              eventType = "ClubMemberContributionAdjusted",
              occurredAt = occurredAt,
              actorId = actor.playerId,
              details = Map(
                "playerId" -> parsedPlayerId.value,
                "delta" -> delta.toString,
                "contribution" -> nextContribution.toString,
                "rankCode" -> updatedClub.rankFor(parsedPlayerId).map(_.code).getOrElse("unknown")
              ),
              note = note
            )
          )
          updatedClub
        ).getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )
