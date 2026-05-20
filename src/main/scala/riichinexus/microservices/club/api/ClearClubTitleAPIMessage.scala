package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ClearClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val parsedPlayerId = PlayerId(playerId)
      val actor = context.support.principal(PlayerId(operatorId))
      val clearedAt = Instant.now()

      module.transactionManager.inTransaction {
        (for
          club <- module.clubRepository.findById(parsedClubId)
          player <- module.playerRepository.findById(parsedPlayerId)
        yield
          ensureClubActive(club)
          requireActivePlayer(player, s"Player ${parsedPlayerId.value} cannot clear club title")
          requireClubMember(club, parsedPlayerId, "clear internal title")
          module.authorizationService.requirePermission(
            actor,
            Permission.SetClubTitle,
            clubId = Some(parsedClubId)
          )

          val existingAssignment = club.titleAssignments.find(_.playerId == parsedPlayerId)
            .getOrElse(
              throw NoSuchElementException(
                s"Player ${parsedPlayerId.value} does not hold a title in club ${parsedClubId.value}"
              )
            )

          val updatedClub = module.clubRepository.save(club.clearInternalTitle(parsedPlayerId))
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "club",
              aggregateId = parsedClubId.value,
              eventType = "ClubTitleCleared",
              occurredAt = clearedAt,
              actorId = actor.playerId,
              details = Map(
                "playerId" -> parsedPlayerId.value,
                "title" -> existingAssignment.title
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
