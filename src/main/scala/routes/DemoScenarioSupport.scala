package routes

import cats.effect.IO
import org.http4s.Request
import riichinexus.domain.model.DemoScenarioVariant

trait DemoScenarioSupport:
  protected def queryParam(request: Request[IO], key: String): Option[String]
  protected def parseEnum[E](label: String, value: String)(parse: String => E): E

  def queryDemoScenarioVariant(request: Request[IO]): DemoScenarioVariant =
    queryParam(request, "variant")
      .filter(_.nonEmpty)
      .map(parseEnum("variant", _)(DemoScenarioVariant.valueOf))
      .getOrElse(DemoScenarioVariant.Basic)
