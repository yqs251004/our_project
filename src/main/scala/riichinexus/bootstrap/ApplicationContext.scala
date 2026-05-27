package riichinexus.bootstrap

import riichinexus.microservices.auth.domain.AuthorizationPolicy
import riichinexus.infrastructure.postgres.{DatabaseConfig, JdbcConnectionFactory}
import riichinexus.system.instrumentation.PerformanceDiagnosticsService

final class ApplicationContext private[bootstrap] (
    val connectionFactory: JdbcConnectionFactory,
    val authModule: AuthModuleContext,
    val playerModule: PlayerModuleContext,
    val clubModule: ClubModuleContext,
    val publicQueryModule: PublicQueryModuleContext,
    val opsAnalyticsModule: OpsAnalyticsModuleContext,
    val tournamentModule: TournamentModuleContext,
    val platformAdminModule: PlatformAdminModuleContext,
    val tournamentAppealModule: TournamentAppealModuleContext,
    private[bootstrap] val repositories: ApplicationRepositoryContext,
    val authorizationService: AuthorizationPolicy,
    val performanceDiagnosticsService: PerformanceDiagnosticsService
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
