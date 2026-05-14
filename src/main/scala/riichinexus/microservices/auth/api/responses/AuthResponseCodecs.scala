package riichinexus.microservices.auth.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object AuthResponseCodecs:
  given ReadWriter[ApiMessage] = macroRW
  given ReadWriter[SessionPrincipalKind] =
    readwriter[String].bimap[SessionPrincipalKind](
      _.toString,
      SessionPrincipalKind.valueOf
    )
  given ReadWriter[CurrentSessionRoleFlags] = macroRW
  given ReadWriter[CurrentSessionView] = macroRW
  given ReadWriter[AuthSuccessView] = macroRW
  given ReadWriter[AuthSessionView] = macroRW
