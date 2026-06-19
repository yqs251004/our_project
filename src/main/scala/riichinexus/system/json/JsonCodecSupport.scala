package riichinexus.system.json

import upickle.default.{ReadWriter, readwriter}

private[json] object JsonCodecSupport:
  def stringEnumReadWriter[A](
      fromString: String => A,
      toStringValue: A => String
  ): ReadWriter[A] =
    readwriter[String].bimap[A](toStringValue, fromString)

  def eitherStringEnumReadWriter[A](
      fromString: String => Either[String, A],
      toStringValue: A => String
  ): ReadWriter[A] =
    stringEnumReadWriter(
      value => fromString(value).fold(message => throw IllegalArgumentException(message), identity),
      toStringValue
    )
