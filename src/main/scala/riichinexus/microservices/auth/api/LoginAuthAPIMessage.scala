package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthenticationFailure
import riichinexus.microservices.auth.objects.apiTypes.AuthSuccessResponse
import riichinexus.microservices.auth.security.AuthPasswordHasher
import upickle.default.*

final case class LoginAuthAPIMessage(
    username: String,
    password: String
) extends APIMessage[AuthSuccessResponse] derives ReadWriter:

  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessResponse] =
    IO {
      val module = context.support.authModule
      module.transactionManager.inTransaction {
        val loginAt = Instant.now()
        val normalizedUsername = AccountCredential.normalizeUsername(username)
        require(password.nonEmpty, "Password is required")

        val credential = module.accountCredentialRepository.findByUsername(normalizedUsername)
          .getOrElse(throw AuthenticationFailure("Invalid username or password", "invalid_credentials"))

        if !AuthPasswordHasher.verify(password, credential) then
          throw AuthenticationFailure("Invalid username or password", "invalid_credentials")

        val player = module.playerTable.find(credential.playerId)
          .getOrElse(throw AuthenticationFailure(s"Player ${credential.playerId.value} was not found", "invalid_credentials"))
        ensureActivePlayer(player)

        val session = module.authenticatedSessionRepository.save(
          AuthenticatedSession.create(
            username = credential.username,
            playerId = credential.playerId,
            createdAt = loginAt,
            ttl = SessionTtl
          )
        )

        riichinexus.microservices.auth.objects.apiTypes.AuthSuccessView(
          userId = player.id,
          username = credential.username,
          displayName = player.nickname,
          token = session.token,
          roles = context.support.registeredRoleFlags(player)
        )
      }
    }

  private def ensureActivePlayer(player: Player): Unit =
    if player.status != PlayerStatus.Active then
      throw AuthenticationFailure(
        s"Player ${player.id.value} is not active",
        "inactive_account"
      )
