package riichinexus.system.json

import riichinexus.microservices.auth.domain.authorization.model.AccessPrincipal
import riichinexus.microservices.auth.domain.account.model.AccountCredential
import riichinexus.microservices.auth.domain.session.model.AuthenticatedSession
import riichinexus.microservices.auth.domain.session.model.GuestAccessSession
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.authorization.`private`.RoleGrant
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.auth.objects.session.SessionPrincipalKind
import riichinexus.system.json.JsonCodecSupport.{eitherStringEnumReadWriter, stringEnumReadWriter}
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, macroRW, read, readwriter, writeJs}

object AuthJsonCodecs:
  given ReadWriter[Role] =
    eitherStringEnumReadWriter(Role.fromString, Role.toString)
  given ReadWriter[Permission] =
    stringEnumReadWriter(Permission.valueOf, _.toString)
  given ReadWriter[RoleGrant] = macroRW
  given ReadWriter[AccessPrincipalPrivateView] =
    readwriter[ujson.Value].bimap[AccessPrincipalPrivateView](
      principal =>
        ujson.Obj(
          "principalId" -> writeJs(principal.principalId),
          "displayName" -> writeJs(principal.displayName),
          "playerId" -> writeJs(principal.playerId),
          "roleGrants" -> writeJs(principal.roleGrants)
        ),
      {
        case obj: ujson.Obj =>
          AccessPrincipalPrivateView(
            principalId = read[String](obj("principalId")),
            displayName = read[String](obj("displayName")),
            playerId = read[Option[riichinexus.microservices.player.objects.PlayerId]](obj("playerId")),
            roleGrants = obj.value.get("roleGrants").fold(Vector.empty[RoleGrant])(read[Vector[RoleGrant]](_))
          )
        case json =>
          throw upickle.core.Abort(s"Expected AccessPrincipalPrivateView object, got $json")
      }
    )
  given ReadWriter[AccountCredential] = macroRW
  given ReadWriter[GuestAccessSession] = macroRW
  given ReadWriter[SessionPrincipalKind] =
    eitherStringEnumReadWriter(
      SessionPrincipalKind.fromString,
      SessionPrincipalKind.toString
    )
  given ReadWriter[AuthenticatedSession] = macroRW
  given ReadWriter[AccessPrincipal] = macroRW
