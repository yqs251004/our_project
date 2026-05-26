package riichinexus.microservices.dictionary.objects

final case class GlobalDictionarySchema(
    entries: Vector[GlobalDictionarySchemaEntry],
    unknownKeyPolicy: String
) derives CanEqual
