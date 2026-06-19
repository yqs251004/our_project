package riichinexus.microservices.auth.security

import java.security.SecureRandom

/** PasswordSaltGenerator 处理密码盐值生成器 相关的认证安全数据或生成逻辑。 */

object PasswordSaltGenerator:
  private val SaltLength = 16
  private val random = SecureRandom()

  def nextSalt(): Vector[Byte] =
    val bytes = new Array[Byte](SaltLength)
    random.nextBytes(bytes)
    bytes.toVector
