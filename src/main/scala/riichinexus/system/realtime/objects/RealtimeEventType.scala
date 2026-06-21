package riichinexus.system.realtime.objects

/** 推送到前端实时事件总线的领域事件类型。
  *
  * 事件类型覆盖通知、俱乐部、申诉、赛事、牌桌、玩家和麻将动作，客户端据此决定刷新哪个本地数据片段。
  */
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
