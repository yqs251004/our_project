package riichinexus.api

import java.sql.Connection

import cats.effect.IO
import riichinexus.api.runtime.ApiPlanSupport

trait APIMessage[Response]:
  def plan(context: ApiPlanContext): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

final case class ApiPlanContext(
    support: ApiPlanSupport,
    bearerToken: Option[String],
    connection: Connection
)

enum ApiSuccessStatus:
  case Ok, Created, Accepted

final case class RegisteredAPIMessage(
    apiName: String,
    requiresBearerToken: Boolean,
    successStatus: ApiSuccessStatus,
    planJson: (String, ApiPlanContext) => IO[ujson.Value]
)
