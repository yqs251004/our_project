package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.objects.apiTypes.AuthSuccessResponse
import riichinexus.microservices.auth.security.AuthPasswordHasher
import upickle.default.*

final case class RegisterAuthAPIMessage(
    username: String,
    password: String,
    displayName: String
) extends APIMessage[AuthSuccessResponse] derives ReadWriter:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessResponse] =
    IO {
      val module = context.support.authModule
      module.transactionManager.inTransaction {
        val registeredAt = Instant.now()
        val normalizedUsername = AccountCredential.normalizeUsername(username)
        val normalizedDisplayName = normalizeDisplayName(displayName)
        validatePassword(password)

        if module.accountCredentialRepository.findByUsername(normalizedUsername).nonEmpty then
          throw IllegalArgumentException(s"Username $normalizedUsername is already registered")

        val player = module.playerRepository.findAll().find(_.userId.equalsIgnoreCase(normalizedUsername)) match
          case Some(existing) if existing.nickname == normalizedDisplayName =>
            existing
          case Some(existing) =>
            module.playerRepository.save(existing.copy(nickname = normalizedDisplayName))
          case None =>
            module.playerRegistration.registerPlayer(
              userId = normalizedUsername,
              nickname = normalizedDisplayName,
              rank = DefaultRank,
              registeredAt = registeredAt
            )
        ensureActivePlayer(player)

        val passwordDigest = AuthPasswordHasher.hash(password)
        module.accountCredentialRepository.save(
          AccountCredential(
            username = normalizedUsername,
            playerId = player.id,
            passwordHash = passwordDigest.hash,
            passwordSalt = passwordDigest.salt,
            passwordIterations = passwordDigest.iterations,
            createdAt = registeredAt,
            updatedAt = registeredAt
          )
        )

        val session = module.authenticatedSessionRepository.save(
          AuthenticatedSession.create(
            username = normalizedUsername,
            playerId = player.id,
            createdAt = registeredAt,
            ttl = SessionTtl
          )
        )

        riichinexus.microservices.auth.objects.apiTypes.AuthSuccessView(
          userId = player.id,
          username = normalizedUsername,
          displayName = player.nickname,
          token = session.token,
          roles = context.support.registeredRoleFlags(player)
        )
      }
    }

  private def normalizeDisplayName(displayName: String): String =
    Option(displayName)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw IllegalArgumentException("Display name is required"))

  private def validatePassword(password: String): Unit =
    require(password.length >= 8, "Password must be at least 8 characters")

  private def ensureActivePlayer(player: Player): Unit =
    if player.status != PlayerStatus.Active then
      throw riichinexus.domain.service.AuthenticationFailure(
        s"Player ${player.id.value} is not active",
        "inactive_account"
      )
