package riichinexus.microservices.dictionary.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.api.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import riichinexus.system.objects.PagedResponse

object DictionaryAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[DictionaryListEntriesAPIMessage, PagedResponse[GlobalDictionaryEntry]],
      RegisteredAPIMessage.api[DictionarySchemaAPIMessage, GlobalDictionarySchemaView],
      RegisteredAPIMessage.api[DictionaryNamespaceBacklogAPIMessage, DictionaryNamespaceBacklogView],
      RegisteredAPIMessage.api[DictionaryListNamespacesAPIMessage, PagedResponse[DictionaryNamespaceRegistration]],
      RegisteredAPIMessage.created[DictionaryRequestNamespaceAPIMessage, DictionaryNamespaceRegistration],
      RegisteredAPIMessage.api[DictionaryReviewNamespaceAPIMessage, DictionaryNamespaceRegistration],
      RegisteredAPIMessage.api[DictionaryTransferNamespaceAPIMessage, DictionaryNamespaceRegistration],
      RegisteredAPIMessage.api[DictionaryUpdateNamespaceCollaboratorsAPIMessage, DictionaryNamespaceRegistration],
      RegisteredAPIMessage.api[DictionaryUpdateNamespaceContextAPIMessage, DictionaryNamespaceRegistration],
      RegisteredAPIMessage.api[DictionaryProcessNamespaceRemindersAPIMessage, Vector[DictionaryNamespaceReminderAction]],
      RegisteredAPIMessage.api[DictionaryRevokeNamespaceAPIMessage, DictionaryNamespaceRegistration],
      RegisteredAPIMessage.api[DictionaryGetEntryAPIMessage, GlobalDictionaryEntry],
      RegisteredAPIMessage.created[DictionaryUpsertEntryAPIMessage, GlobalDictionaryEntry]
    )
