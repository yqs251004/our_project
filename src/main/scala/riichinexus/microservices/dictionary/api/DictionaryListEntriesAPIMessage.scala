package riichinexus.microservices.dictionary.api

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class DictionaryListEntriesAPIMessage(
    prefix: Option[String] = None,
    updatedBy: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[GlobalDictionaryEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[GlobalDictionaryEntry]] =
    IO {
      val parsedPrefix = prefix.filter(_.nonEmpty)
      val parsedUpdatedBy = updatedBy.filter(_.nonEmpty).map(PlayerId(_))
      val entries = context.support.dictionaryModule.tables.listEntries()
        .filter(entry => parsedPrefix.forall(prefix => entry.key.startsWith(prefix)))
        .filter(entry => parsedUpdatedBy.forall(_ == entry.updatedBy))
        .sortBy(_.key)
      page(entries, filters(prefix.filter(_.nonEmpty).map("prefix" -> _), updatedBy.filter(_.nonEmpty).map("updatedBy" -> _)))
    }

  private def page(items: Vector[GlobalDictionaryEntry], appliedFilters: Map[String, String]): PagedResponse[GlobalDictionaryEntry] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
