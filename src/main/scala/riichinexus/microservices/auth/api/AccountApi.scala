package riichinexus.microservices.auth.api

import java.time.Instant

import riichinexus.microservices.auth.api.requests.{LoginRequest, RegisterAccountRequest}
import riichinexus.microservices.auth.api.responses.AuthSuccessView

object AccountApi:

  def register(
      service: AuthApplicationService,
      request: RegisterAccountRequest,
      registeredAt: Instant = Instant.now()
  ): AuthSuccessView =
    service.register(
      username = request.username,
      password = request.password,
      displayName = request.displayName,
      registeredAt = registeredAt
    )

  def login(
      service: AuthApplicationService,
      request: LoginRequest,
      loginAt: Instant = Instant.now()
  ): AuthSuccessView =
    service.login(
      username = request.username,
      password = request.password,
      loginAt = loginAt
    )
