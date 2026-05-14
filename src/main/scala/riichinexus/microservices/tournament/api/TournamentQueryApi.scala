package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.api.responses.*
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.tables.TournamentTables

object TournamentQueryApi:

  def listTournaments(
      tables: TournamentTables,
      query: TournamentListQuery
  ): Vector[Tournament] =
    tables.listTournaments(
      status = query.status,
      adminId = query.adminId,
      organizer = query.organizer
    )
      .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))

  def findTournamentDetail(
      views: TournamentViewAssembler,
      tournamentId: TournamentId
  ) =
    views.buildTournamentDetailView(tournamentId)

  def stageDirectory(
      tables: TournamentTables,
      views: TournamentViewAssembler,
      tournamentId: TournamentId
  ): Vector[TournamentStageDirectoryEntry] =
    tables.findTournament(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      .stages
      .sortBy(_.order)
      .map(views.buildTournamentStageDirectoryEntry)

  def whitelist(
      tables: TournamentTables,
      tournamentId: TournamentId,
      query: TournamentWhitelistQuery
  ): Vector[TournamentWhitelistEntry] =
    tables.findTournament(tournamentId)
      .map(_.whitelist
        .filter(entry => query.participantKind.forall(_ == entry.participantKind))
        .filter(entry => query.playerId.forall(id => entry.playerId.contains(id)))
        .filter(entry => query.clubId.forall(id => entry.clubId.contains(id)))
      )
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
