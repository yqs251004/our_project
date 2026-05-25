package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.{GlobalDictionaryEntry as GlobalDictionaryEntryResponse}
import upickle.default.*

final case class DictionaryGetEntryAPIMessage(
    key: String
) extends APIMessage[GlobalDictionaryEntryResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionaryEntryResponse] =
    IO {
      context.support.dictionaryModule.tables.findEntryByKey(key)
        .map(GlobalDictionaryEntryResponse.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
