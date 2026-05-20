package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class DictionaryGetEntryAPIMessage(
    key: String
) extends APIMessage[GlobalDictionaryEntry] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionaryEntry] =
    IO {
      context.support.dictionaryModule.tables.findEntryByKey(key)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
