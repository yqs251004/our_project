package riichinexus.microservices.dictionary.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.api.*
import riichinexus.microservices.dictionary.objects.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse

object DictionaryAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[DictionaryListEntriesAPIMessage, PagedResponse[GlobalDictionaryEntryView]],
      RegisteredAPIMessage.api[DictionarySchemaAPIMessage, GlobalDictionarySchemaView],
      RegisteredAPIMessage.api[DictionaryNamespaceBacklogAPIMessage, DictionaryNamespaceBacklogView],
      RegisteredAPIMessage.api[DictionaryListNamespacesAPIMessage, PagedResponse[DictionaryNamespaceRegistrationView]],
      RegisteredAPIMessage.created[DictionaryRequestNamespaceAPIMessage, DictionaryNamespaceRegistrationView],
      RegisteredAPIMessage.api[DictionaryReviewNamespaceAPIMessage, DictionaryNamespaceRegistrationView],
      RegisteredAPIMessage.api[DictionaryTransferNamespaceAPIMessage, DictionaryNamespaceRegistrationView],
      RegisteredAPIMessage.api[DictionaryUpdateNamespaceCollaboratorsAPIMessage, DictionaryNamespaceRegistrationView],
      RegisteredAPIMessage.api[DictionaryUpdateNamespaceContextAPIMessage, DictionaryNamespaceRegistrationView],
      RegisteredAPIMessage.api[DictionaryProcessNamespaceRemindersAPIMessage, Vector[DictionaryNamespaceReminderActionView]],
      RegisteredAPIMessage.api[DictionaryRevokeNamespaceAPIMessage, DictionaryNamespaceRegistrationView],
      RegisteredAPIMessage.api[DictionaryGetEntryAPIMessage, GlobalDictionaryEntryView],
      RegisteredAPIMessage.created[DictionaryUpsertEntryAPIMessage, GlobalDictionaryEntryView]
    )
