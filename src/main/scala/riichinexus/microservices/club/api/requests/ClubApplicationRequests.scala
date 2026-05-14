package riichinexus.microservices.club.api.requests

import riichinexus.domain.model.{GuestSessionId, PlayerId}
import upickle.default.*

final case class ClubMembershipApplicationRequest(
    applicantUserId: Option[String],
    displayName: String,
    message: Option[String] = None,
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None
):
  def session: Option[GuestSessionId] =
    guestSessionId.map(GuestSessionId(_))

  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object ClubMembershipApplicationRequest:
  given ReadWriter[ClubMembershipApplicationRequest] = macroRW

final case class ApproveClubApplicationRequest(
    playerId: String,
    operatorId: String,
    note: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

object ApproveClubApplicationRequest:
  given ReadWriter[ApproveClubApplicationRequest] = macroRW

final case class RejectClubApplicationRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object RejectClubApplicationRequest:
  given ReadWriter[RejectClubApplicationRequest] = macroRW

final case class ReviewClubApplicationRequest(
    operatorId: String,
    decision: String,
    playerId: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def player: Option[PlayerId] =
    playerId.map(PlayerId(_))

object ReviewClubApplicationRequest:
  given ReadWriter[ReviewClubApplicationRequest] = macroRW

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
