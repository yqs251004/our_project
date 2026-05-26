package riichinexus.microservices.dictionary.objects.apiTypes

import upickle.default.*

final case class DictionaryListNamespacesQuery(
    operatorId: String,
    status: Option[String] = None,
    contextClubId: Option[String] = None,
    ownerId: Option[String] = None,
    requestedBy: Option[String] = None,
    reviewedBy: Option[String] = None,
    asOf: Option[String] = None,
    overdueOnly: Option[Boolean] = None,
    dueBefore: Option[String] = None,
    dueAfter: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object DictionaryListNamespacesQuery:
  given ReadWriter[DictionaryListNamespacesQuery] = macroRW
