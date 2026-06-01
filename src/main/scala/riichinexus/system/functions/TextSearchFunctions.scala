package riichinexus.system.functions

object TextSearchFunctions:

  def containsIgnoreCase(value: String, fragment: String): Boolean =
    value.toLowerCase.contains(fragment.toLowerCase)
