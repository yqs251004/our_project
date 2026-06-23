package riichinexus.microservices.club.domain.membership.functions

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.domain.membership.model.ClubMembershipApplication
import riichinexus.microservices.club.objects.membership.ClubApplicationStatus

/** ClubMembershipApplicationFunctions 提供俱乐部成员资格申请相关的领域计算、校验和转换函数。 */

private[club] object ClubMembershipApplicationFunctions:
  def isPending(application: ClubMembershipApplication): Boolean =
    application.status == ClubApplicationStatus.Pending

  def approve(
      application: ClubMembershipApplication,
      by: PlayerId,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be approved")
    application.copy(
      status = ClubApplicationStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(
      application: ClubMembershipApplication,
      by: PlayerId,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be rejected")
    application.copy(
      status = ClubApplicationStatus.Rejected,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def withdraw(
      application: ClubMembershipApplication,
      byPrincipalId: String,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be withdrawn")
    application.copy(
      status = ClubApplicationStatus.Withdrawn,
      reviewedAt = Some(at),
      reviewNote = note,
      withdrawnByPrincipalId = Some(byPrincipalId)
    )

