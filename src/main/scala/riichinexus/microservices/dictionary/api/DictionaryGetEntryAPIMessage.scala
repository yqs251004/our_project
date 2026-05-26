package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.GlobalDictionaryEntryView
import riichinexus.microservices.dictionary.tables.globaldictionary.GlobalDictionaryTable
import upickle.default.*

final case class DictionaryGetEntryAPIMessage(
    key: String
) extends APIMessage[GlobalDictionaryEntryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionaryEntryView] =
    for
      entry <- IO(
        GlobalDictionaryTable.findByKey(context.connection, key)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
    yield GlobalDictionaryEntryView.fromDomain(entry)
