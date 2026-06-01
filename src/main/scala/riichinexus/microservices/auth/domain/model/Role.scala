package riichinexus.microservices.auth.domain.model

enum Role derives CanEqual:
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
