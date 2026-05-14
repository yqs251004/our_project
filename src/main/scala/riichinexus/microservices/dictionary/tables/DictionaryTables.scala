package riichinexus.microservices.dictionary.tables

import riichinexus.application.ports.{DictionaryNamespaceRepository, GlobalDictionaryRepository}
import riichinexus.domain.model.*

final class DictionaryTables(
    globalDictionaryRepository: GlobalDictionaryRepository,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository
):
  def listEntries(): Vector[GlobalDictionaryEntry] =
    globalDictionaryRepository.findAll()

  def findEntryByKey(key: String): Option[GlobalDictionaryEntry] =
    globalDictionaryRepository.findByKey(key)

  def listNamespaces(): Vector[DictionaryNamespaceRegistration] =
    dictionaryNamespaceRepository.findAll()

  def findNamespaceByPrefix(namespacePrefix: String): Option[DictionaryNamespaceRegistration] =
    dictionaryNamespaceRepository.findByPrefix(namespacePrefix)

  def listApprovedNamespaces(): Vector[DictionaryNamespaceRegistration] =
    listNamespaces().filter(_.status == DictionaryNamespaceReviewStatus.Approved)

object DictionaryTables:
  val OwnedTables: Vector[String] = Vector(
    "global_dictionary",
    "dictionary_namespaces"
  )
