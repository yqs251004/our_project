package riichinexus.microservices.club.objects.apiTypes

import riichinexus.microservices.club.domain.model.{ClubMemberPrivilegeSnapshot as DomainClubMemberPrivilegeSnapshot}
import riichinexus.microservices.club.objects.ClubPrivilegeCode
import upickle.default.*

final case class ClubMemberPrivilegeSnapshotView(
    playerId: String,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[ClubPrivilegeCode],
    isAdmin: Boolean,
    internalTitle: Option[String]
) derives ReadWriter

object ClubMemberPrivilegeSnapshotView:
  def fromDomain(snapshot: DomainClubMemberPrivilegeSnapshot): ClubMemberPrivilegeSnapshotView =
    ClubMemberPrivilegeSnapshotView(
      playerId = snapshot.playerId.value,
      contribution = snapshot.contribution,
      rankCode = snapshot.rankCode,
      rankLabel = snapshot.rankLabel,
      privileges = snapshot.privileges,
      isAdmin = snapshot.isAdmin,
      internalTitle = snapshot.internalTitle
    )
