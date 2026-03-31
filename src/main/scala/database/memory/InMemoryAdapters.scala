package database.memory

import ports.*

type InMemoryPlayerRepository = riichinexus.infrastructure.memory.InMemoryPlayerRepository
type InMemoryClubRepository = riichinexus.infrastructure.memory.InMemoryClubRepository
type InMemoryTournamentRepository = riichinexus.infrastructure.memory.InMemoryTournamentRepository
type InMemoryTableRepository = riichinexus.infrastructure.memory.InMemoryTableRepository
type InMemoryAppealTicketRepository = riichinexus.infrastructure.memory.InMemoryAppealTicketRepository
type InMemoryDashboardRepository = riichinexus.infrastructure.memory.InMemoryDashboardRepository
type InMemoryAdvancedStatsBoardRepository = riichinexus.infrastructure.memory.InMemoryAdvancedStatsBoardRepository
type InMemoryAdvancedStatsRecomputeTaskRepository = riichinexus.infrastructure.memory.InMemoryAdvancedStatsRecomputeTaskRepository
type InMemoryGlobalDictionaryRepository = riichinexus.infrastructure.memory.InMemoryGlobalDictionaryRepository
type InMemoryDictionaryNamespaceRepository = riichinexus.infrastructure.memory.InMemoryDictionaryNamespaceRepository
type InMemoryTournamentSettlementRepository = riichinexus.infrastructure.memory.InMemoryTournamentSettlementRepository

object InMemoryPlayerRepository:
  def apply(): InMemoryPlayerRepository =
    new riichinexus.infrastructure.memory.InMemoryPlayerRepository()

object InMemoryClubRepository:
  def apply(): InMemoryClubRepository =
    new riichinexus.infrastructure.memory.InMemoryClubRepository()

object InMemoryTournamentRepository:
  def apply(): InMemoryTournamentRepository =
    new riichinexus.infrastructure.memory.InMemoryTournamentRepository()

object InMemoryTableRepository:
  def apply(): InMemoryTableRepository =
    new riichinexus.infrastructure.memory.InMemoryTableRepository()

object InMemoryAppealTicketRepository:
  def apply(): InMemoryAppealTicketRepository =
    new riichinexus.infrastructure.memory.InMemoryAppealTicketRepository()

object InMemoryDashboardRepository:
  def apply(): InMemoryDashboardRepository =
    new riichinexus.infrastructure.memory.InMemoryDashboardRepository()

object InMemoryAdvancedStatsBoardRepository:
  def apply(): InMemoryAdvancedStatsBoardRepository =
    new riichinexus.infrastructure.memory.InMemoryAdvancedStatsBoardRepository()

object InMemoryAdvancedStatsRecomputeTaskRepository:
  def apply(): InMemoryAdvancedStatsRecomputeTaskRepository =
    new riichinexus.infrastructure.memory.InMemoryAdvancedStatsRecomputeTaskRepository()

object InMemoryGlobalDictionaryRepository:
  def apply(): InMemoryGlobalDictionaryRepository =
    new riichinexus.infrastructure.memory.InMemoryGlobalDictionaryRepository()

object InMemoryDictionaryNamespaceRepository:
  def apply(): InMemoryDictionaryNamespaceRepository =
    new riichinexus.infrastructure.memory.InMemoryDictionaryNamespaceRepository()

object InMemoryTournamentSettlementRepository:
  def apply(): InMemoryTournamentSettlementRepository =
    new riichinexus.infrastructure.memory.InMemoryTournamentSettlementRepository()
