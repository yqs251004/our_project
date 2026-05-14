package riichinexus.microservices.dictionary.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object DictionaryResponseCodecs:
  given ReadWriter[GlobalDictionarySchemaView] = macroRW
  given ReadWriter[DictionaryNamespaceOwnerBacklog] = macroRW
  given ReadWriter[DictionaryNamespaceBacklogView] = macroRW
