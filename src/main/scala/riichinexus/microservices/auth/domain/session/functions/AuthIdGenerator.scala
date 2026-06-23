package riichinexus.microservices.auth.domain.session.functions

import riichinexus.microservices.auth.domain.session.model.AuthIdPrefix
import riichinexus.microservices.auth.objects.session.GuestSessionId

import java.util.UUID

/** AuthIdGenerator 负责生成认证标识符生成器 相关的领域标识符。 */

private[auth] object AuthIdGenerator:
  private def nextId(prefix: AuthIdPrefix): String =
    s"${AuthIdPrefix.toString(prefix)}-${UUID.randomUUID().toString.take(8)}"

  def guestSessionId(): GuestSessionId = GuestSessionId(nextId(AuthIdPrefix.GuestSession))
