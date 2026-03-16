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
