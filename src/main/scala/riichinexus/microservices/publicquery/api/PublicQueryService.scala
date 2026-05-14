package riichinexus.microservices.publicquery.api

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.{AuthorizationService, NoOpAuthorizationService}
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.{
  PublicClubDirectoryEntryResponse,
  PublicClubLeaderboardEntryResponse,
  PublicPlayerLeaderboardEntryResponse,
  PublicScheduleResponse
}
import riichinexus.microservices.publicquery.tables.PublicQueryTables

final class PublicQueryService(
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val guestPrincipal = AccessPrincipal.guest()
  private val tables = PublicQueryTables(
    tournamentRepository = tournamentRepository,
    tableRepository = tableRepository,
    playerRepository = playerRepository,
    clubRepository = clubRepository,
    globalDictionaryRepository = globalDictionaryRepository
  )

  def publicSchedules(): Vector[PublicScheduleResponse] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicSchedule)
    tables.publicSchedules()

  def publicClubDirectory(): Vector[PublicClubDirectoryEntryResponse] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewClubDirectory)
    tables.publicClubDirectory()

  def publicPlayerLeaderboard(limit: Int = 100): Vector[PublicPlayerLeaderboardEntryResponse] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicLeaderboard)
    tables.publicPlayerLeaderboard(limit)

  def publicClubLeaderboard(limit: Int = 100): Vector[PublicClubLeaderboardEntryResponse] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicLeaderboard)
    tables.publicClubLeaderboard(limit)
