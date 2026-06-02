package riichinexus.system

import scala.util.Try

object EnumParsing:

  def parse[E](label: String, value: String)(parseValue: String => E): E =
    Try(parseValue(value)).getOrElse(throw IllegalArgumentException(s"Invalid $label: $value"))
