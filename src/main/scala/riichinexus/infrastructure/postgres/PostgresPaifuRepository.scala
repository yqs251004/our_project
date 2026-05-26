package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.tables.paifu.PaifuTable

final class PostgresPaifuRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PaifuRepository:
  override def save(paifu: Paifu): Paifu =
    connectionFactory.withConnection(PaifuTable.save(_, paifu))

  override def findById(id: PaifuId): Option[Paifu] =
    connectionFactory.withConnection(PaifuTable.findById(_, id))

  override def findAll(): Vector[Paifu] =
    connectionFactory.withConnection(PaifuTable.findAll)

  override def findByPlayer(playerId: PlayerId): Vector[Paifu] =
    connectionFactory.withConnection(PaifuTable.findByPlayer(_, playerId))

object PostgresPaifuRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresPaifuRepository =
    new PostgresPaifuRepository(connectionFactory)
