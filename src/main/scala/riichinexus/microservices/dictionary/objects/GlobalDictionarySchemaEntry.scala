package riichinexus.microservices.dictionary.objects

final case class GlobalDictionarySchemaEntry(
    id: String,
    keyPattern: String,
    valueType: GlobalDictionaryValueType,
    description: String,
    validationHint: String,
    runtimeConsumers: Vector[String],
    examples: Vector[String]
) derives CanEqual
