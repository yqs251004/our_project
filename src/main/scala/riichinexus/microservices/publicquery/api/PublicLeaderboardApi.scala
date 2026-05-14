package riichinexus.microservices.publicquery.api

import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.{
  PublicClubLeaderboardEntryResponse,
  PublicPlayerLeaderboardEntryResponse
}
import riichinexus.microservices.publicquery.objects.{PublicClubLeaderboardQuery, PublicPlayerLeaderboardQuery}

object PublicLeaderboardApi:

  def playerLeaderboard(
      service: PublicQueryService,
      query: PublicPlayerLeaderboardQuery
  ): Vector[PublicPlayerLeaderboardEntryResponse] =
    service.publicPlayerLeaderboard(Int.MaxValue)
      .filter(entry => query.clubId.forall(entry.clubIds.contains))
      .filter(entry => query.status.forall(_ == entry.status))

  def clubLeaderboard(
      service: PublicQueryService,
      query: PublicClubLeaderboardQuery,
      containsIgnoreCase: (String, String) => Boolean
  ): Vector[PublicClubLeaderboardEntryResponse] =
    service.publicClubLeaderboard(Int.MaxValue)
      .filter(entry => query.name.forall(containsIgnoreCase(entry.name, _)))
