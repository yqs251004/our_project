package riichinexus

import java.time.Instant

import riichinexus.bootstrap.ApplicationContext

@main def riichiNexusDemo(): Unit =
  val app = ApplicationContext.fromEnvironment()
  val snapshot = app.demoScenarioService.bootstrapBasicScenario()

  println("== Updated Players ==")
  snapshot.players.foreach { player =>
    println(
      s"${player.nickname}: elo=${player.elo}, clubs=${player.clubIds.map(_.value).mkString(",")}, superAdmin=${player.isSuperAdmin}"
    )
  }

  println()
  println("== Demo Snapshot ==")
  println(
    s"seededAt=${snapshot.seededAt}, operatorId=${snapshot.recommendedOperatorId.value}, guestSession=${snapshot.guestSessionId.map(_.value).getOrElse("none")}"
  )
  println(
    s"tournament=${snapshot.tournament.name}, status=${snapshot.tournament.status}, stage=${snapshot.tournament.stageName}, tables=${snapshot.tournament.tableIds.map(_.value).mkString(",")}"
  )
