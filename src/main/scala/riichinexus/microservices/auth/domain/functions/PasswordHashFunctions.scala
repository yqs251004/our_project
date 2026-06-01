package riichinexus.microservices.auth.domain.functions

import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import riichinexus.microservices.auth.domain.model.AccountCredential
import riichinexus.microservices.auth.security.PasswordDigest

object PasswordHashFunctions:
  val DefaultIterations: Int = 65_536

  private val Algorithm = "PBKDF2WithHmacSHA256"
  private val KeyLengthBits = 256

  def digest(password: String, salt: Vector[Byte], iterations: Int = DefaultIterations): PasswordDigest =
    require(iterations > 0, "Password hash iterations must be positive")
    PasswordDigest(
      hash = encode(derive(password, salt.toArray, iterations)),
      salt = encode(salt.toArray),
      iterations = iterations
    )

  def verify(
      password: String,
      credential: AccountCredential
  ): Boolean =
    val salt = Base64.getDecoder.decode(credential.passwordSalt).toVector
    val derived = digest(password, salt, credential.passwordIterations)
    derived.hash == credential.passwordHash

  private def derive(password: String, salt: Array[Byte], iterations: Int): Array[Byte] =
    val spec = PBEKeySpec(password.toCharArray, salt, iterations, KeyLengthBits)
    try SecretKeyFactory.getInstance(Algorithm).generateSecret(spec).getEncoded
    finally spec.clearPassword()

  private def encode(bytes: Array[Byte]): String =
    Base64.getEncoder.encodeToString(bytes)
