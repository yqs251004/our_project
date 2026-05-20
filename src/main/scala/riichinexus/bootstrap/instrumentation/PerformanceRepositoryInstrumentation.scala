package riichinexus.bootstrap.instrumentation

import riichinexus.bootstrap.ApplicationRepositoryContext
import riichinexus.application.ports.*
import riichinexus.domain.model.*

object PerformanceRepositoryInstrumentation:
  def instrument(
      repositories: ApplicationRepositoryContext,
      diagnostics: PerformanceDiagnosticsService
  ): ApplicationRepositoryContext =
    repositories.copy(
      playerRepository =
        new InstrumentedPlayerRepository(repositories.playerRepository, diagnostics),
      clubRepository =
        new InstrumentedClubRepository(repositories.clubRepository, diagnostics),
      globalDictionaryRepository =
        new InstrumentedGlobalDictionaryRepository(
          repositories.globalDictionaryRepository,
          diagnostics
        ),
      tournamentRepository =
        new InstrumentedTournamentRepository(repositories.tournamentRepository, diagnostics),
      tableRepository =
        new InstrumentedTableRepository(repositories.tableRepository, diagnostics),
      matchRecordRepository =
        new InstrumentedMatchRecordRepository(repositories.matchRecordRepository, diagnostics)
    )

private trait RepositoryInstrumentationProbe:
  protected def diagnostics: PerformanceDiagnosticsService
  protected def repositoryName: String

  protected def instrument[A](operation: String)(body: => A): A =
    val startedAt = System.nanoTime()
    try body
    finally
      diagnostics.recordRepositoryCall(
        repository = repositoryName,
        operation = operation,
        durationNanos = System.nanoTime() - startedAt
      )

private final class InstrumentedPlayerRepository(
    delegate: PlayerRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends PlayerRepository
    with RepositoryInstrumentationProbe:
  protected val repositoryName = "PlayerRepository"

  def save(player: Player): Player = instrument("save")(delegate.save(player))
  def findById(id: PlayerId): Option[Player] = instrument("findById")(delegate.findById(id))
  def findByUserId(userId: String): Option[Player] = instrument("findByUserId")(delegate.findByUserId(userId))
  def findAll(): Vector[Player] = instrument("findAll")(delegate.findAll())
  override def findByIds(ids: Vector[PlayerId]): Vector[Player] = instrument("findByIds")(delegate.findByIds(ids))
  override def findByClub(clubId: ClubId): Vector[Player] = instrument("findByClub")(delegate.findByClub(clubId))

private final class InstrumentedClubRepository(
    delegate: ClubRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends ClubRepository
    with RepositoryInstrumentationProbe:
  protected val repositoryName = "ClubRepository"

  def save(club: Club): Club = instrument("save")(delegate.save(club))
  def findById(id: ClubId): Option[Club] = instrument("findById")(delegate.findById(id))
  def findByName(name: String): Option[Club] = instrument("findByName")(delegate.findByName(name))
  def findAll(): Vector[Club] = instrument("findAll")(delegate.findAll())
  override def findByIds(ids: Vector[ClubId]): Vector[Club] = instrument("findByIds")(delegate.findByIds(ids))
  override def findFiltered(
      activeOnly: Boolean,
      joinableOnly: Boolean,
      memberId: Option[PlayerId],
      adminId: Option[PlayerId],
      name: Option[String]
  ): Vector[Club] =
    instrument("findFiltered")(delegate.findFiltered(activeOnly, joinableOnly, memberId, adminId, name))
  override def findActive(): Vector[Club] = instrument("findActive")(delegate.findActive())

private final class InstrumentedTournamentRepository(
    delegate: TournamentRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends TournamentRepository
    with RepositoryInstrumentationProbe:
  protected val repositoryName = "TournamentRepository"

  def save(tournament: Tournament): Tournament = instrument("save")(delegate.save(tournament))
  def findById(id: TournamentId): Option[Tournament] = instrument("findById")(delegate.findById(id))
  def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament] =
    instrument("findByNameAndOrganizer")(delegate.findByNameAndOrganizer(name, organizer))
  def findAll(): Vector[Tournament] = instrument("findAll")(delegate.findAll())
  override def findByIds(ids: Vector[TournamentId]): Vector[Tournament] = instrument("findByIds")(delegate.findByIds(ids))
  override def findFiltered(
      status: Option[TournamentStatus],
      adminId: Option[PlayerId],
      organizer: Option[String],
      includeDraft: Boolean
  ): Vector[Tournament] =
    instrument("findFiltered")(delegate.findFiltered(status, adminId, organizer, includeDraft))
  override def findByClub(clubId: ClubId): Vector[Tournament] = instrument("findByClub")(delegate.findByClub(clubId))
  override def findPublic(): Vector[Tournament] = instrument("findPublic")(delegate.findPublic())
  override def findByAdmin(playerId: PlayerId): Vector[Tournament] = instrument("findByAdmin")(delegate.findByAdmin(playerId))

private final class InstrumentedTableRepository(
    delegate: TableRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends TableRepository
    with RepositoryInstrumentationProbe:
  protected val repositoryName = "TableRepository"

  def save(table: Table): Table = instrument("save")(delegate.save(table))
  def delete(id: TableId): Unit = instrument("delete")(delegate.delete(id))
  def findById(id: TableId): Option[Table] = instrument("findById")(delegate.findById(id))
  def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    instrument("findByTournamentAndStage")(delegate.findByTournamentAndStage(tournamentId, stageId))
  def findAll(): Vector[Table] = instrument("findAll")(delegate.findAll())
  override def findByTournamentIds(tournamentIds: Vector[TournamentId]): Vector[Table] =
    instrument("findByTournamentIds")(delegate.findByTournamentIds(tournamentIds))
  override def findByStatus(status: TableStatus): Vector[Table] =
    instrument("findByStatus")(delegate.findByStatus(status))

private final class InstrumentedMatchRecordRepository(
    delegate: MatchRecordRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends MatchRecordRepository
    with RepositoryInstrumentationProbe:
  protected val repositoryName = "MatchRecordRepository"

  def save(record: MatchRecord): MatchRecord = instrument("save")(delegate.save(record))
  def findById(id: MatchRecordId): Option[MatchRecord] = instrument("findById")(delegate.findById(id))
  def findByTable(tableId: TableId): Option[MatchRecord] = instrument("findByTable")(delegate.findByTable(tableId))
  def findAll(): Vector[MatchRecord] = instrument("findAll")(delegate.findAll())
  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    instrument("findByTournamentAndStage")(delegate.findByTournamentAndStage(tournamentId, stageId))
  override def findRecentByClub(clubId: ClubId, limit: Int): Vector[MatchRecord] =
    instrument("findRecentByClub")(delegate.findRecentByClub(clubId, limit))
  override def findByPlayer(playerId: PlayerId): Vector[MatchRecord] =
    instrument("findByPlayer")(delegate.findByPlayer(playerId))

private final class InstrumentedGlobalDictionaryRepository(
    delegate: GlobalDictionaryRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends GlobalDictionaryRepository
    with RepositoryInstrumentationProbe:
  protected val repositoryName = "GlobalDictionaryRepository"

  def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry = instrument("save")(delegate.save(entry))
  def findByKey(key: String): Option[GlobalDictionaryEntry] = instrument("findByKey")(delegate.findByKey(key))
  def findAll(): Vector[GlobalDictionaryEntry] = instrument("findAll")(delegate.findAll())
