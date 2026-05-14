package riichinexus.microservices.auth.tables

import riichinexus.application.ports.{GuestSessionRepository, PlayerRepository}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.objects.GuestSessionListQuery

final class AuthTables(
    playerRepository: PlayerRepository,
    guestSessionRepository: GuestSessionRepository
):
  def listGuestSessions(query: GuestSessionListQuery): Vector[GuestAccessSession] =
    guestSessionRepository.findAll()
      .filter(session => query.activeOnly.forall(flag => !flag || session.canAuthenticate(query.asOf)))
      .sortBy(session => (session.createdAt, session.id.value))

  def findGuestSession(sessionId: GuestSessionId): Option[GuestAccessSession] =
    guestSessionRepository.findById(sessionId)

  def findPlayer(playerId: PlayerId): Option[Player] =
    playerRepository.findById(playerId)

object AuthTables:
  val OwnedTables: Vector[String] = Vector(
    "players",
    "guest_sessions"
  )
