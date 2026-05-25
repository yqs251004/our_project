package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.{GlobalDictionaryEntry as GlobalDictionaryEntryResponse}
import upickle.default.*

final case class DictionaryGetEntryAPIMessage(
    key: String
) extends APIMessage[GlobalDictionaryEntryResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionaryEntryResponse] =
    for
      entry <- IO(
        context.support.dictionaryModule.tables.findEntryByKey(key)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
    yield GlobalDictionaryEntryResponse.fromDomain(entry)
