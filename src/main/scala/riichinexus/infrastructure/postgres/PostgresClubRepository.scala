package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.club.tables.club.ClubTable

final class PostgresClubRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends ClubRepository:
  override def save(club: Club): Club =
    connectionFactory.withConnection(ClubTable.save(_, club))

  override def findById(id: ClubId): Option[Club] =
    connectionFactory.withConnection(ClubTable.findById(_, id))

  override def findByName(name: String): Option[Club] =
    connectionFactory.withConnection(ClubTable.findByName(_, name))

  override def findByIds(ids: Vector[ClubId]): Vector[Club] =
    connectionFactory.withConnection(ClubTable.findByIds(_, ids))

  override def findFiltered(
      activeOnly: Boolean = false,
      joinableOnly: Boolean = false,
      memberId: Option[PlayerId] = None,
      adminId: Option[PlayerId] = None,
      name: Option[String] = None
  ): Vector[Club] =
    connectionFactory.withConnection(
      ClubTable.findFiltered(
        _,
        activeOnly = activeOnly,
        joinableOnly = joinableOnly,
        memberId = memberId,
        adminId = adminId,
        name = name
      )
    )

  override def findActive(): Vector[Club] =
    connectionFactory.withConnection(ClubTable.findActive)

  override def findAll(): Vector[Club] =
    connectionFactory.withConnection(ClubTable.findAll)

object PostgresClubRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresClubRepository =
    new PostgresClubRepository(connectionFactory)
