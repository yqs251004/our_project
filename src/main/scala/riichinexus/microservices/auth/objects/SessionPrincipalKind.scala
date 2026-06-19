package riichinexus.microservices.auth.objects

/** SessionPrincipalKind 枚举会话访问主体类型 可使用的公开取值。 */

enum SessionPrincipalKind:
  case Anonymous
  case Guest
  case RegisteredPlayer

object SessionPrincipalKind:

  def toString(kind: SessionPrincipalKind): String =
    kind match
      case SessionPrincipalKind.Anonymous        => "Anonymous"
      case SessionPrincipalKind.Guest            => "Guest"
      case SessionPrincipalKind.RegisteredPlayer => "RegisteredPlayer"

  def fromString(value: String): Either[String, SessionPrincipalKind] =
    value.trim match
      case "Anonymous"        => Right(SessionPrincipalKind.Anonymous)
      case "Guest"            => Right(SessionPrincipalKind.Guest)
      case "RegisteredPlayer" => Right(SessionPrincipalKind.RegisteredPlayer)
      case other              => Left(s"Unsupported SessionPrincipalKind value: $other")
