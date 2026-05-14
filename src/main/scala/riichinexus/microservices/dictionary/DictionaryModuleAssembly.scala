package riichinexus.microservices.dictionary

import riichinexus.application.ports.{DomainEventBus, TransactionManager}
import riichinexus.bootstrap.{ApplicationRepositoryContext, DictionaryModuleContext}
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.dictionary.api.DictionaryGovernanceService
import riichinexus.microservices.dictionary.tables.DictionaryTables

object DictionaryModuleAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      eventBus: DomainEventBus,
      transactionManager: TransactionManager,
      authorizationService: AuthorizationService
  ): DictionaryModuleContext =
    DictionaryModuleContext(
      tables = DictionaryTables(
        globalDictionaryRepository = repositories.globalDictionaryRepository,
        dictionaryNamespaceRepository = repositories.dictionaryNamespaceRepository
      ),
      governance = DictionaryGovernanceService(
        playerRepository = repositories.playerRepository,
        clubRepository = repositories.clubRepository,
        globalDictionaryRepository = repositories.globalDictionaryRepository,
        dictionaryNamespaceRepository = repositories.dictionaryNamespaceRepository,
        auditEventRepository = repositories.auditEventRepository,
        eventBus = eventBus,
        transactionManager = transactionManager,
        authorizationService = authorizationService
      )
    )
