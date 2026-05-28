package riichinexus.microservices.auth.objects

import riichinexus.microservices.auth.domain.model.{RoleKind as DomainRoleKind}
import upickle.default.*

enum Role derives CanEqual:
  case Guest
  case RegisteredPlayer
  case ClubAdmin
  case TournamentAdmin
  case SuperAdmin

  def toDomain: DomainRoleKind =
    DomainRoleKind.valueOf(toString)

object Role:
  given ReadWriter[Role] = readwriter[String].bimap(_.toString, Role.valueOf)

  def fromDomain(role: DomainRoleKind): Role =
    Role.valueOf(role.toString)
