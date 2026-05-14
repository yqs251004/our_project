package riichinexus.bootstrap

import riichinexus.domain.service.AuthorizationService
import riichinexus.infrastructure.postgres.DatabaseConfig

final class ApplicationContext private[bootstrap] (
    val authModule: AuthModuleContext,
    val playerModule: PlayerModuleContext,
    val clubModule: ClubModuleContext,
    val dictionaryModule: DictionaryModuleContext,
    val publicQueryModule: PublicQueryModuleContext,
    val opsAnalyticsModule: OpsAnalyticsModuleContext,
    val tournamentModule: TournamentModuleContext,
    val platformAdminModule: PlatformAdminModuleContext,
    val tournamentAppealModule: TournamentAppealModuleContext,
    private[bootstrap] val repositories: ApplicationRepositoryContext,
    val authorizationService: AuthorizationService
)

object ApplicationContext:

  def fromEnvironment(
      env: collection.Map[String, String] = sys.env
  ): ApplicationContext =
    ApplicationAssembly.fromEnvironment(env)

  def inMemory(): ApplicationContext =
    ApplicationAssembly.inMemory()

  def postgres(config: DatabaseConfig): ApplicationContext =
    ApplicationAssembly.postgres(config)

  def postgres(config: TemplateDatabaseConfig): ApplicationContext =
    ApplicationAssembly.postgres(config)
