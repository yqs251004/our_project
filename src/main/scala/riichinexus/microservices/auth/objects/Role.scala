package riichinexus.microservices.auth.objects

import upickle.default.*

enum Role derives CanEqual:
  case Guest
  case RegisteredPlayer
  case ClubAdmin
  case TournamentAdmin
  case SuperAdmin

object Role:
  given ReadWriter[Role] = readwriter[String].bimap(_.toString, Role.valueOf)
