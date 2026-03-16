package riichinexus.domain.model

enum GlobalDictionaryValueType derives CanEqual:
  case Integer
  case Decimal
  case Weight
  case RatioVector
  case StageRuleTemplate
  case Metadata

final case class GlobalDictionarySchemaEntry(
    id: String,
    keyPattern: String,
    valueType: GlobalDictionaryValueType,
    description: String,
    validationHint: String,
    runtimeConsumers: Vector[String],
    examples: Vector[String]
) derives CanEqual

final case class GlobalDictionarySchemaView(
    entries: Vector[GlobalDictionarySchemaEntry],
    unknownKeyPolicy: String
) derives CanEqual

enum DictionaryNamespaceReviewStatus derives CanEqual:
  case Pending
  case Approved
  case Rejected

final case class DictionaryNamespaceRegistration(
    namespacePrefix: String,
    ownerPlayerId: PlayerId,
    requestedBy: PlayerId,
    requestedAt: java.time.Instant,
    status: DictionaryNamespaceReviewStatus = DictionaryNamespaceReviewStatus.Pending,
    reviewedBy: Option[PlayerId] = None,
    reviewedAt: Option[java.time.Instant] = None,
    reviewNote: Option[String] = None
) derives CanEqual:
  require(namespacePrefix.trim.nonEmpty, "Dictionary namespace prefix cannot be empty")

  def approve(by: PlayerId, at: java.time.Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Pending, "Only pending namespace requests can be approved")
    copy(
      status = DictionaryNamespaceReviewStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(by: PlayerId, at: java.time.Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Pending, "Only pending namespace requests can be rejected")
    copy(
      status = DictionaryNamespaceReviewStatus.Rejected,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

