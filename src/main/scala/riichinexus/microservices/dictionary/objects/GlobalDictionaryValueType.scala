package riichinexus.microservices.dictionary.objects

enum GlobalDictionaryValueType:
  case Integer
  case Decimal
  case Weight
  case RatioVector
  case StageRuleTemplate
  case Metadata

object GlobalDictionaryValueType:
  def toString(valueType: GlobalDictionaryValueType): String =
    valueType match
      case GlobalDictionaryValueType.Integer           => "Integer"
      case GlobalDictionaryValueType.Decimal           => "Decimal"
      case GlobalDictionaryValueType.Weight            => "Weight"
      case GlobalDictionaryValueType.RatioVector       => "RatioVector"
      case GlobalDictionaryValueType.StageRuleTemplate => "StageRuleTemplate"
      case GlobalDictionaryValueType.Metadata          => "Metadata"

  def fromString(value: String): Either[String, GlobalDictionaryValueType] =
    value.trim match
      case "Integer"           => Right(GlobalDictionaryValueType.Integer)
      case "Decimal"           => Right(GlobalDictionaryValueType.Decimal)
      case "Weight"            => Right(GlobalDictionaryValueType.Weight)
      case "RatioVector"       => Right(GlobalDictionaryValueType.RatioVector)
      case "StageRuleTemplate" => Right(GlobalDictionaryValueType.StageRuleTemplate)
      case "Metadata"          => Right(GlobalDictionaryValueType.Metadata)
      case other               => Left(s"Unsupported GlobalDictionaryValueType value: $other")
