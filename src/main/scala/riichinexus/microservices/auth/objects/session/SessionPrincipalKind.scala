package riichinexus.microservices.auth.objects.session

/** 当前请求在会话层解析出的主体类别。
  *
  * 该枚举区分匿名访问、游客会话和已注册玩家会话，让接口可以在返回当前会话视图时准确表达身份来源。
  */
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
