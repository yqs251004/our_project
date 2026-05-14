import cats.effect.{IO, IOApp, Resource}
import org.http4s.server.Server
import org.typelevel.log4cats.slf4j.Slf4jLogger
import riichinexus.api.{ApiRuntimeContext, ApiServer, ApiServerConfig}
import riichinexus.bootstrap.DatabaseSession

object Main extends IOApp.Simple:

  private val logger = Slf4jLogger.getLogger[IO]

  private val serverResource: Resource[IO, Server] =
    for
      normalizedEnv <- Resource.eval(IO(DatabaseSession.normalizedEnvironment(sys.env)))
      _ <- Resource.eval(DatabaseSession.initialize(normalizedEnv))
      app <- Resource.eval(IO(DatabaseSession.applicationContext(normalizedEnv)))
      config = ApiServerConfig.fromEnv(normalizedEnv).copy(
        storageLabel = DatabaseSession.storageLabel(normalizedEnv)
      )
      runtime = ApiRuntimeContext.fromApplication(app, config)
      server <- ApiServer.resource(runtime, config)
    yield server

  override def run: IO[Unit] =
    for
      _ <- logger.info("Starting template-aligned Riichi Nexus backend")
      _ <- serverResource.useForever
    yield ()
