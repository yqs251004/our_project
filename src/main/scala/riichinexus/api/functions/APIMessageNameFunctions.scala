package riichinexus.api.functions

import scala.reflect.ClassTag

object APIMessageNameFunctions:

  def apiNameFromClassName(className: String): String =
    val objectName = className.stripSuffix("$")
    val baseName = objectName.stripSuffix("APIMessage")
    s"${baseName}API".toLowerCase

  def nameOf[Message](using classTag: ClassTag[Message]): String =
    apiNameFromClassName(classTag.runtimeClass.getSimpleName)
