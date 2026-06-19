package riichinexus.system.realtime.objects

/** RealtimeEventType 枚举前端实时事件总线的事件类型。 */
enum RealtimeEventType:
  case NotificationCreated
  case ClubApplicationChanged
  case ClubMemberChanged
  case ClubChanged
  case AppealChanged
  case TournamentTableChanged
  case TournamentChanged
  case PlayerChanged
  case DomainChanged
  case MahjongActionAccepted

object RealtimeEventType:
  def toString(eventType: RealtimeEventType): String =
    eventType.toString

  def fromString(value: String): RealtimeEventType =
    RealtimeEventType.valueOf(value)
