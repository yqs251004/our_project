package riichinexus.microservices.club.api.requests

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CreateClubRequest(
    name: String,
    creatorId: String
):
  def creator: PlayerId =
    PlayerId(creatorId)

object CreateClubRequest:
  given ReadWriter[CreateClubRequest] = macroRW

final case class AddClubMemberRequest(
    playerId: String,
    operatorId: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object AddClubMemberRequest:
  given ReadWriter[AddClubMemberRequest] = macroRW

final case class AssignClubTitleRequest(
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

object AssignClubTitleRequest:
  given ReadWriter[AssignClubTitleRequest] = macroRW

final case class ClearClubTitleRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object ClearClubTitleRequest:
  given ReadWriter[ClearClubTitleRequest] = macroRW

final case class AdjustClubTreasuryRequest(
    operatorId: String,
    delta: Long,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object AdjustClubTreasuryRequest:
  given ReadWriter[AdjustClubTreasuryRequest] = macroRW

final case class AdjustClubPointPoolRequest(
    operatorId: String,
    delta: Int,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object AdjustClubPointPoolRequest:
  given ReadWriter[AdjustClubPointPoolRequest] = macroRW

final case class AdjustClubMemberContributionRequest(
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def player: PlayerId =
    PlayerId(playerId)

object AdjustClubMemberContributionRequest:
  given ReadWriter[AdjustClubMemberContributionRequest] = macroRW

final case class ClubRankNodeRequest(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[String] = Vector.empty
):
  def toNode: ClubRankNode =
    ClubRankNode(
      code = code,
      label = label,
      minimumContribution = minimumContribution,
      privileges = privileges
    )

object ClubRankNodeRequest:
  given ReadWriter[ClubRankNodeRequest] = macroRW

final case class UpdateClubRankTreeRequest(
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def nodes: Vector[ClubRankNode] =
    ranks.map(_.toNode)

object UpdateClubRankTreeRequest:
  given ReadWriter[UpdateClubRankTreeRequest] = macroRW

final case class AwardClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def honor: ClubHonor =
    ClubHonor(
      title = title,
      achievedAt = achievedAt.getOrElse(Instant.now()),
      note = note
    )

object AwardClubHonorRequest:
  given ReadWriter[AwardClubHonorRequest] = macroRW

final case class RevokeClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object RevokeClubHonorRequest:
  given ReadWriter[RevokeClubHonorRequest] = macroRW

final case class UpdateClubRecruitmentPolicyRequest(
    operatorId: String,
    applicationsOpen: Boolean,
    requirementsText: Option[String] = None,
    expectedReviewSlaHours: Option[Int] = None,
    note: Option[String] = None
):
  expectedReviewSlaHours.foreach(hours =>
    require(hours > 0, "Recruitment policy expectedReviewSlaHours must be positive")
  )

  def operator: PlayerId =
    PlayerId(operatorId)

  def policy: ClubRecruitmentPolicy =
    ClubRecruitmentPolicy(
      applicationsOpen = applicationsOpen,
      requirementsText = requirementsText.map(_.trim).filter(_.nonEmpty),
      expectedReviewSlaHours = expectedReviewSlaHours
    )

object UpdateClubRecruitmentPolicyRequest:
  given ReadWriter[UpdateClubRecruitmentPolicyRequest] = macroRW

final case class UpdateClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def toRelation(updatedAt: Instant = Instant.now()): ClubRelation =
    ClubRelation(
      targetClubId = ClubId(targetClubId),
      relation = ClubRelationKind.valueOf(relation),
      updatedAt = updatedAt,
      note = note
    )

object UpdateClubRelationRequest:
  given ReadWriter[UpdateClubRelationRequest] = macroRW
