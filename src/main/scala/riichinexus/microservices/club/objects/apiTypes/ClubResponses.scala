package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.{
  Club as DomainClub,
  ClubMemberPrivilegeSnapshot as DomainClubMemberPrivilegeSnapshot,
  ClubPrivilegeDefinition as DomainClubPrivilegeDefinition,
  ClubRelation as DomainClubRelation
}

type Club = DomainClub
type ClubRelation = DomainClubRelation
type PlayerProfileView = riichinexus.microservices.player.objects.apiTypes.PlayerProfileView
type ClubPrivilegeDefinition = DomainClubPrivilegeDefinition
type ClubMemberPrivilegeSnapshot = DomainClubMemberPrivilegeSnapshot
