package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class CurrentSessionQuery(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
)

object CurrentSessionQuery:
  given ReadWriter[CurrentSessionQuery] = macroRW

final case class CreateGuestSessionRequest(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
):
  ttlHours.foreach(hours => require(hours > 0, "Guest session ttlHours must be positive"))

object CreateGuestSessionRequest:
  given ReadWriter[CreateGuestSessionRequest] = macroRW

final case class RevokeGuestSessionRequest(
    reason: Option[String] = None
)

object RevokeGuestSessionRequest:
  given ReadWriter[RevokeGuestSessionRequest] = macroRW

final case class UpgradeGuestSessionRequest(
    playerId: String
):
  def player: PlayerId =
    PlayerId(playerId)

object UpgradeGuestSessionRequest:
  given ReadWriter[UpgradeGuestSessionRequest] = macroRW

final case class ListGuestSessionsRequest(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ListGuestSessionsRequest:
  given ReadWriter[ListGuestSessionsRequest] = macroRW
