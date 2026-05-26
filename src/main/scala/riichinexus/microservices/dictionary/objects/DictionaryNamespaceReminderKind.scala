package riichinexus.microservices.dictionary.objects

enum DictionaryNamespaceReminderKind:
  case DueSoon
  case Overdue
  case Escalated

object DictionaryNamespaceReminderKind:
  def toString(kind: DictionaryNamespaceReminderKind): String =
    kind match
      case DictionaryNamespaceReminderKind.DueSoon   => "DueSoon"
      case DictionaryNamespaceReminderKind.Overdue   => "Overdue"
      case DictionaryNamespaceReminderKind.Escalated => "Escalated"

  def fromString(value: String): Either[String, DictionaryNamespaceReminderKind] =
    value.trim match
      case "DueSoon"   => Right(DictionaryNamespaceReminderKind.DueSoon)
      case "Overdue"   => Right(DictionaryNamespaceReminderKind.Overdue)
      case "Escalated" => Right(DictionaryNamespaceReminderKind.Escalated)
      case other       => Left(s"Unsupported DictionaryNamespaceReminderKind value: $other")
