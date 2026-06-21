package riichinexus.microservices.auth.objects

/** 系统内可授予访问主体的角色级别。
  *
  * 角色既包括游客和注册玩家这样的基础身份，也包括俱乐部、赛事和平台管理范围，用于生成后续权限集合。
  */
enum Role:
  case Guest
  case RegisteredPlayer
  case ClubAdmin
  case TournamentAdmin
  case SuperAdmin

object Role:
  def toString(role: Role): String =
    role match
      case Role.Guest            => "Guest"
      case Role.RegisteredPlayer => "RegisteredPlayer"
      case Role.ClubAdmin        => "ClubAdmin"
      case Role.TournamentAdmin  => "TournamentAdmin"
      case Role.SuperAdmin       => "SuperAdmin"

  def fromString(value: String): Either[String, Role] =
    value match
      case "Guest"            => Right(Role.Guest)
      case "RegisteredPlayer" => Right(Role.RegisteredPlayer)
      case "ClubAdmin"        => Right(Role.ClubAdmin)
      case "TournamentAdmin"  => Right(Role.TournamentAdmin)
      case "SuperAdmin"       => Right(Role.SuperAdmin)
      case other              => Left(s"Unsupported Role value: $other")
