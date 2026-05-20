package riichinexus.microservices.auth.tables.guestsession

import java.time.Instant

import riichinexus.application.ports.GuestSessionRepository
import riichinexus.domain.model.{GuestAccessSession, GuestSessionId}

final class GuestSessionTable(
    guestSessionRepository: GuestSessionRepository
):
  def list(
      activeOnly: Option[Boolean] = None,
      asOf: Instant = Instant.now()
  ): Vector[GuestAccessSession] =
    guestSessionRepository.findAll()
      .filter(session => activeOnly.forall(flag => !flag || session.canAuthenticate(asOf)))
      .sortBy(session => (session.createdAt, session.id.value))

  def find(sessionId: GuestSessionId): Option[GuestAccessSession] =
    guestSessionRepository.findById(sessionId)
