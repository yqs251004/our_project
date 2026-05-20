package riichinexus.microservices.dictionary.api

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import upickle.default.*

final case class DictionarySchemaAPIMessage() extends APIMessage[GlobalDictionarySchemaView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionarySchemaView] =
    IO {
      val schema = GlobalDictionaryRegistry.schemaView
      GlobalDictionarySchemaView(
        entries = schema.entries,
        unknownKeyPolicy = schema.unknownKeyPolicy
      )
    }
