package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.{GuestSessionId, PlayerId}
import upickle.default.*

final case class WithdrawClubApplicationRequest(
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None,
    note: Option[String] = None
):
  def session: Option[GuestSessionId] =
    guestSessionId.map(GuestSessionId(_))

  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object WithdrawClubApplicationRequest:
  given ReadWriter[WithdrawClubApplicationRequest] = macroRW
