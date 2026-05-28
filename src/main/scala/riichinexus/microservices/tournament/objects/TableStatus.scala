package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{TableStatus as DomainTableStatus}
import upickle.default.*

enum TableStatus derives CanEqual:
  case WaitingPreparation
  case InProgress
  case Scoring
  case Archived
  case AppealInProgress

  def toDomain: DomainTableStatus =
    DomainTableStatus.valueOf(toString)

object TableStatus:
  val Pending: TableStatus = WaitingPreparation
  val Finished: TableStatus = Archived

  given ReadWriter[TableStatus] = readwriter[String].bimap(_.toString, TableStatus.valueOf)

  def fromDomain(status: DomainTableStatus): TableStatus =
    TableStatus.valueOf(status.toString)
