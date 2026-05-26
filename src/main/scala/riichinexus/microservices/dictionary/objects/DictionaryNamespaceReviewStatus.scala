package riichinexus.microservices.dictionary.objects

enum DictionaryNamespaceReviewStatus:
  case Pending
  case Approved
  case Rejected
  case Revoked

object DictionaryNamespaceReviewStatus:
  def toString(status: DictionaryNamespaceReviewStatus): String =
    status match
      case DictionaryNamespaceReviewStatus.Pending  => "Pending"
      case DictionaryNamespaceReviewStatus.Approved => "Approved"
      case DictionaryNamespaceReviewStatus.Rejected => "Rejected"
      case DictionaryNamespaceReviewStatus.Revoked  => "Revoked"

  def fromString(value: String): Either[String, DictionaryNamespaceReviewStatus] =
    value.trim match
      case "Pending"  => Right(DictionaryNamespaceReviewStatus.Pending)
      case "Approved" => Right(DictionaryNamespaceReviewStatus.Approved)
      case "Rejected" => Right(DictionaryNamespaceReviewStatus.Rejected)
      case "Revoked"  => Right(DictionaryNamespaceReviewStatus.Revoked)
      case other      => Left(s"Unsupported DictionaryNamespaceReviewStatus value: $other")
