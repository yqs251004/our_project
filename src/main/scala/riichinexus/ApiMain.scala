package riichinexus

import java.util.concurrent.CountDownLatch

import riichinexus.api.*
import riichinexus.bootstrap.ApplicationContext

@main def riichiNexusApi(): Unit =
  val app = ApplicationContext.fromEnvironment()
  val config = ApiServerConfig.fromEnv()
  val server = RiichiNexusApiServer(app, config)

  server.start()
  println(s"RiichiNexus API listening on http://${config.host}:${config.port}")
  println(s"Storage mode: ${config.storageLabel}")
  println("Press Ctrl+C to stop the server.")

  Runtime.getRuntime.addShutdownHook(
    Thread(() => server.stop())
  )

  CountDownLatch(1).await()
