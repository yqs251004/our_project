package riichinexus

import java.util.concurrent.CountDownLatch

import _root_.api.{ApiServer, ApiServerConfig}
import _root_.database.ApplicationContext

@main def riichiNexusApi(): Unit =
  val app = ApplicationContext.fromEnvironment()
  val config = ApiServerConfig.fromEnv()
  val server = new ApiServer(app, config)

  server.start()
  println(s"RiichiNexus API listening on http://${config.host}:${config.port}")
  println(s"Storage mode: ${config.storageLabel}")
  println("Press Ctrl+C to stop the server.")

  Runtime.getRuntime.addShutdownHook(
    Thread(() => server.stop())
  )

  CountDownLatch(1).await()
