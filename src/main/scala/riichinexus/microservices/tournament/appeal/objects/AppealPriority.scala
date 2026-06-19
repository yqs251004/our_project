package riichinexus.microservices.tournament.appeal.objects

/** AppealPriority 枚举申诉优先级 可使用的公开取值。 */

enum AppealPriority:
  case Low
  case Normal
  case High
  case Critical

object AppealPriority:
  def toString(priority: AppealPriority): String =
    priority.toString

  def fromString(value: String): AppealPriority =
    AppealPriority.valueOf(value)
