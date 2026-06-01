package riichinexus.api.functions

import cats.effect.IO
import riichinexus.api.{APIMessage, APIWithTokenMessage, ApiPlanContext, ApiSuccessStatus, RegisteredAPIMessage}
import upickle.default.*

import scala.reflect.ClassTag

object RegisteredAPIMessageFunctions:

  def api[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Ok)

  def created[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Created)

  def accepted[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Accepted)

  def apiWithToken[Message <: APIWithTokenMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = true, successStatus = ApiSuccessStatus.Ok)

  private def build[Message <: APIMessage[Response], Response](
      requiresBearerToken: Boolean,
      successStatus: ApiSuccessStatus
  )(using
      reader: Reader[Message],
      writer: Writer[Response],
      classTag: ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = APIMessageNameFunctions.nameOf[Message],
      requiresBearerToken = requiresBearerToken,
      successStatus = successStatus,
      planJson = (body: String, context: ApiPlanContext) =>
        for
          message <- IO.blocking(read[Message](body)(using reader))
          response <- message.plan(context)
          json <- IO.blocking(writeJs(response)(using writer))
        yield json
    )
