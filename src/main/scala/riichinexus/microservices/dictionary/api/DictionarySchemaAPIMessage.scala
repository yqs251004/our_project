package riichinexus.microservices.dictionary.api

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import upickle.default.*

final case class DictionarySchemaAPIMessage() extends APIMessage[GlobalDictionarySchemaView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionarySchemaView] =
    for
      schema <- IO(GlobalDictionaryRegistry.schemaView)
    yield
      GlobalDictionarySchemaView(
        entries = schema.entries.map(GlobalDictionarySchemaEntry.fromDomain),
        unknownKeyPolicy = schema.unknownKeyPolicy
      )
