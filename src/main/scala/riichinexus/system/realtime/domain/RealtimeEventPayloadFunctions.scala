package riichinexus.system.realtime.domain

import upickle.default.{Writer, writeJs}

object RealtimeEventPayloadFunctions:
  def toJson[A: Writer](value: A): ujson.Value =
    writeJs(value)
