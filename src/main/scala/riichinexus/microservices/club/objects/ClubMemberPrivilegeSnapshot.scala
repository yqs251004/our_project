package riichinexus.microservices.club.objects

import riichinexus.domain.model.{ClubMemberPrivilegeSnapshot as DomainClubMemberPrivilegeSnapshot}
import upickle.default.*

final case class ClubMemberPrivilegeSnapshot(
    playerId: String,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[String],
    isAdmin: Boolean,
    internalTitle: Option[String]
) derives ReadWriter

object ClubMemberPrivilegeSnapshot:
  def fromDomain(snapshot: DomainClubMemberPrivilegeSnapshot): ClubMemberPrivilegeSnapshot =
    ClubMemberPrivilegeSnapshot(
      playerId = snapshot.playerId.value,
      contribution = snapshot.contribution,
      rankCode = snapshot.rankCode,
      rankLabel = snapshot.rankLabel,
      privileges = snapshot.privileges,
      isAdmin = snapshot.isAdmin,
      internalTitle = snapshot.internalTitle
    )
