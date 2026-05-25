package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.domain.model.GuestAccessSession
import upickle.default.*

final case class GuestSessionResponse(
    id: String,
    displayName: String,
    createdAt: String
) derives ReadWriter

object GuestSessionResponse:
  def fromDomain(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )
