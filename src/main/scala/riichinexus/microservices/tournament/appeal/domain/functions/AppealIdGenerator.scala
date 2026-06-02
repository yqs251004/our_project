package riichinexus.microservices.tournament.appeal.domain.functions

import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId

import java.util.UUID

object AppealIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def appealTicketId(): AppealTicketId = AppealTicketId(nextId("appeal"))
