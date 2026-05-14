package riichinexus.infrastructure.postgres

import java.sql.SQLException

import org.postgresql.util.PSQLException
private[postgres] object PostgresErrors:
  private val UniqueViolation = "23505"

  def isUniqueViolation(error: SQLException, constraint: String): Boolean =
    Option(error.getSQLState).contains(UniqueViolation) &&
      constraintName(error).contains(constraint)

  private def constraintName(error: SQLException): Option[String] =
    error match
      case postgresError: PSQLException =>
        Option(postgresError.getServerErrorMessage).map(_.getConstraint)
      case _ =>
        None
