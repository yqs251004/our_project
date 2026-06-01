package riichinexus.microservices.auth.security

import java.security.SecureRandom

object PasswordSaltGenerator:
  private val SaltLength = 16
  private val random = SecureRandom()

  def nextSalt(): Vector[Byte] =
    val bytes = new Array[Byte](SaltLength)
    random.nextBytes(bytes)
    bytes.toVector
