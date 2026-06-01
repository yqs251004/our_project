package riichinexus.system.functions

import scala.util.Try

object EnumParsingFunctions:

  def parse[E](label: String, value: String)(parseValue: String => E): E =
    Try(parseValue(value)).getOrElse(throw IllegalArgumentException(s"Invalid $label: $value"))
