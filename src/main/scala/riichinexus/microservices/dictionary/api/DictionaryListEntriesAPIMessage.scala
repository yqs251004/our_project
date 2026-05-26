package riichinexus.microservices.dictionary.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.{GlobalDictionaryEntry, GlobalDictionaryEntryView}
import riichinexus.microservices.dictionary.tables.globaldictionary.GlobalDictionaryTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class DictionaryListEntriesAPIMessage(
    prefix: Option[String] = None,
    updatedBy: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[GlobalDictionaryEntryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[GlobalDictionaryEntryView]] =
    for
      query <- IO(resolveQuery)
      entries <- IO(listEntries(context, query))
    yield
      PagedResponse.fromItems(entries, limit, offset, query.appliedFilters)(
        GlobalDictionaryEntryView.fromDomain
      )

  private def resolveQuery: ResolvedEntriesQuery =
    ResolvedEntriesQuery(
      prefix = prefix.filter(_.nonEmpty),
      updatedBy = updatedBy.filter(_.nonEmpty).map(PlayerId(_)),
      appliedFilters = Vector(
        prefix.filter(_.nonEmpty).map("prefix" -> _),
        updatedBy.filter(_.nonEmpty).map("updatedBy" -> _)
      ).flatten.toMap
    )

  private def listEntries(
      context: ApiPlanContext,
      query: ResolvedEntriesQuery
  ): Vector[GlobalDictionaryEntry] =
    GlobalDictionaryTable.findAll(context.connection)
      .filter(entry => query.prefix.forall(prefix => entry.key.startsWith(prefix)))
      .filter(entry => query.updatedBy.forall(_ == entry.updatedBy))
      .sortBy(_.key)

  private final case class ResolvedEntriesQuery(
      prefix: Option[String],
      updatedBy: Option[PlayerId],
      appliedFilters: Map[String, String]
  )
