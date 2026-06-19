package riichinexus.microservices.tournament.appeal.domain.functions

import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId

import java.util.UUID

/** AppealIdGenerator 负责生成申诉标识符生成器 相关的领域标识符。 */

private[appeal] object AppealIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def appealTicketId(): AppealTicketId = AppealTicketId(nextId("appeal"))
