package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.tables.tournament.TournamentTable

final class PostgresTournamentRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentRepository:
  override def save(tournament: Tournament): Tournament =
    connectionFactory.withConnection(TournamentTable.save(_, tournament))

  override def findById(id: TournamentId): Option[Tournament] =
    connectionFactory.withConnection(TournamentTable.findById(_, id))

  override def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament] =
    connectionFactory.withConnection(TournamentTable.findByNameAndOrganizer(_, name, organizer))

  override def findByIds(ids: Vector[TournamentId]): Vector[Tournament] =
    connectionFactory.withConnection(TournamentTable.findByIds(_, ids))

  override def findFiltered(
      status: Option[TournamentStatus] = None,
      adminId: Option[PlayerId] = None,
      organizer: Option[String] = None,
      includeDraft: Boolean = true
  ): Vector[Tournament] =
    connectionFactory.withConnection(
      TournamentTable.findFiltered(
        _,
        status = status,
        adminId = adminId,
        organizer = organizer,
        includeDraft = includeDraft
      )
    )

  override def findByClub(clubId: ClubId): Vector[Tournament] =
    connectionFactory.withConnection(TournamentTable.findByClub(_, clubId))

  override def findPublic(): Vector[Tournament] =
    connectionFactory.withConnection(TournamentTable.findPublic)

  override def findAll(): Vector[Tournament] =
    connectionFactory.withConnection(TournamentTable.findAll)

object PostgresTournamentRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTournamentRepository =
    new PostgresTournamentRepository(connectionFactory)
