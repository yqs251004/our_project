package riichinexus.microservices.publicquery.api

import riichinexus.domain.model.ClubId
import riichinexus.microservices.club.api.ClubViewAssembler
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.{
  PublicClubDetailResponse,
  PublicClubDirectoryEntryResponse
}
import riichinexus.microservices.publicquery.objects.PublicClubDirectoryQuery

object PublicClubApi:

  def listClubs(
      service: PublicQueryService,
      query: PublicClubDirectoryQuery,
      containsIgnoreCase: (String, String) => Boolean
  ): Vector[PublicClubDirectoryEntryResponse] =
    service.publicClubDirectory()
      .filter(club => query.name.forall(containsIgnoreCase(club.name, _)))
      .filter(club => query.relation.forall(relation => club.relations.exists(_.relation == relation)))
      .sortBy(_.name)

  def detail(
      views: ClubViewAssembler,
      clubId: ClubId
  ): Option[PublicClubDetailResponse] =
    views.buildPublicClubDetailView(clubId)
