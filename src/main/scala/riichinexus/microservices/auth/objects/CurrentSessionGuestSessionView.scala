package riichinexus.microservices.auth.objects

import riichinexus.microservices.auth.domain.model.GuestAccessSession
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionGuestSessionView(
    id: String,
    displayName: String
) derives CanEqual

object CurrentSessionGuestSessionView:
  given ReadWriter[CurrentSessionGuestSessionView] = macroRW

  def fromDomain(session: GuestAccessSession): CurrentSessionGuestSessionView =
    CurrentSessionGuestSessionView(
      id = session.id.value,
      displayName = session.displayName
    )
