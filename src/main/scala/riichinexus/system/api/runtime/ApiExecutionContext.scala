package riichinexus.system.api.runtime

import riichinexus.system.postgres.JdbcConnectionFactory

final case class ApiExecutionContext(
    connectionFactory: JdbcConnectionFactory,
    storageLabel: String
)
