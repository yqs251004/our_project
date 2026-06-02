import cats.effect.{IO, IOApp, Resource}
import org.http4s.server.Server
import org.typelevel.log4cats.slf4j.Slf4jLogger
import riichinexus.system.api.{ApiRuntimeContext, ApiServer, ApiServerConfig}
import riichinexus.system.DatabaseSession

object Main extends IOApp.Simple:

  private val logger = Slf4jLogger.getLogger[IO]

  private val serverResource: Resource[IO, Server] =
    for
      normalizedEnv <- Resource.eval(IO.blocking(DatabaseSession.normalizedEnvironment(sys.env)))
      connectionFactory <- Resource.eval(IO.blocking(DatabaseSession.connectionFactory(normalizedEnv)))
      _ <- Resource.eval(DatabaseSession.initialize(connectionFactory))
      config = ApiServerConfig.fromEnv(normalizedEnv).copy(
        storageLabel = DatabaseSession.storageLabel(normalizedEnv)
      )
      runtime = ApiRuntimeContext.fromConnectionFactory(connectionFactory, config)
      server <- ApiServer.resource(runtime, config)
    yield server

  override def run: IO[Unit] =
    for
      _ <- logger.info("Starting template-aligned Riichi Nexus backend")
      _ <- serverResource.useForever
    yield ()
