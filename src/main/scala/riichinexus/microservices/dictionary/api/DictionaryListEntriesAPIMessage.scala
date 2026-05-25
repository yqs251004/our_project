package riichinexus.microservices.dictionary.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.{GlobalDictionaryEntry as GlobalDictionaryEntryResponse}
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class DictionaryListEntriesAPIMessage(
    prefix: Option[String] = None,
    updatedBy: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[GlobalDictionaryEntryResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[GlobalDictionaryEntryResponse]] =
    for
      query <- IO(resolveQuery)
      entries <- IO(listEntries(context, query))
    yield
      PagedResponse.fromItems(entries, limit, offset, query.appliedFilters)(
        GlobalDictionaryEntryResponse.fromDomain
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
    context.support.dictionaryModule.tables.listEntries()
      .filter(entry => query.prefix.forall(prefix => entry.key.startsWith(prefix)))
      .filter(entry => query.updatedBy.forall(_ == entry.updatedBy))
      .sortBy(_.key)

  private final case class ResolvedEntriesQuery(
      prefix: Option[String],
      updatedBy: Option[PlayerId],
      appliedFilters: Map[String, String]
  )
