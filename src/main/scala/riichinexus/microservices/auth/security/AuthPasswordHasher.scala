package riichinexus.microservices.auth.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import riichinexus.domain.model.AccountCredential

object AuthPasswordHasher:
  private val Algorithm = "PBKDF2WithHmacSHA256"
  private val KeyLengthBits = 256
  private val DefaultIterations = 65_536
  private val SaltLength = 16
  private val random = SecureRandom()

  final case class PasswordDigest(
      hash: String,
      salt: String,
      iterations: Int
  )

  def hash(password: String): PasswordDigest =
    val saltBytes = new Array[Byte](SaltLength)
    random.nextBytes(saltBytes)
    val iterations = DefaultIterations
    PasswordDigest(
      hash = encode(derive(password, saltBytes, iterations)),
      salt = encode(saltBytes),
      iterations = iterations
    )

  def verify(
      password: String,
      credential: AccountCredential
  ): Boolean =
    val derived = derive(password, Base64.getDecoder.decode(credential.passwordSalt), credential.passwordIterations)
    encode(derived) == credential.passwordHash

  private def derive(password: String, salt: Array[Byte], iterations: Int): Array[Byte] =
    val spec = PBEKeySpec(password.toCharArray, salt, iterations, KeyLengthBits)
    try SecretKeyFactory.getInstance(Algorithm).generateSecret(spec).getEncoded
    finally spec.clearPassword()

  private def encode(bytes: Array[Byte]): String =
    Base64.getEncoder.encodeToString(bytes)
