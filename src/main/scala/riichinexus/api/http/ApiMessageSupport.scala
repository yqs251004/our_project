package riichinexus.api.http

import cats.effect.IO
import org.http4s.{Request, Response}
import upickle.default.*

final case class ApiMessageHandler(
    name: String,
    handle: (RouteSupport, Request[IO]) => IO[Response[IO]]
)

final case class ApiMessageContract(
    messageName: String,
    inputType: String,
    outputType: String,
    ownerService: String,
    oldRestRoute: String,
    status: String
) derives CanEqual

object ApiMessageContract:
  given ReadWriter[ApiMessageContract] = macroRW

final case class EmptyApiMessageInput() derives CanEqual

object EmptyApiMessageInput:
  given ReadWriter[EmptyApiMessageInput] = macroRW
