package riichinexus.microservices.publicquery.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.publicquery.api.*
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicQueryResponses.given
import riichinexus.system.objects.apiTypes.PagedResponse

object PublicQueryAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[ListPublicSchedulesAPIMessage, PagedResponse[PublicScheduleView]],
      RegisteredAPIMessage.api[ListPublicTournamentsAPIMessage, PagedResponse[PublicTournamentSummaryView]],
      RegisteredAPIMessage.api[GetPublicTournamentAPIMessage, PublicTournamentDetailView],
      RegisteredAPIMessage.api[ListPublicClubsAPIMessage, PagedResponse[PublicClubDirectoryEntry]],
      RegisteredAPIMessage.api[GetPublicClubAPIMessage, PublicClubDetailView],
      RegisteredAPIMessage.api[PublicPlayerLeaderboardAPIMessage, PagedResponse[PlayerLeaderboardEntry]],
      RegisteredAPIMessage.api[PublicClubLeaderboardAPIMessage, PagedResponse[ClubLeaderboardEntry]]
    )
