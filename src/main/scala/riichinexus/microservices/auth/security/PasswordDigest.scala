package riichinexus.microservices.auth.security

/** PasswordDigest 处理密码Digest 相关的认证安全数据或生成逻辑。 */

final case class PasswordDigest(
    hash: String,
    salt: String,
    iterations: Int
)
