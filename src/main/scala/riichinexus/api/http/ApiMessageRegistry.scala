package riichinexus.api.http

import riichinexus.microservices.auth.api.messages.AuthApiMessages
import riichinexus.microservices.club.api.messages.ClubApiMessages
import riichinexus.microservices.dictionary.api.messages.DictionaryApiMessages
import riichinexus.microservices.opsanalytics.api.messages.OpsAnalyticsApiMessages
import riichinexus.microservices.platformadmin.api.messages.PlatformAdminApiMessages
import riichinexus.microservices.tournament.api.messages.TournamentApiMessages
import riichinexus.microservices.tournament.appeal.api.messages.TournamentAppealApiMessages
import riichinexus.microservices.player.api.messages.PlayerApiMessages
import riichinexus.microservices.publicquery.api.messages.PublicQueryApiMessages

object ApiMessageRegistry:
  private val registeredHandlers: Vector[ApiMessageHandler] =
    AuthApiMessages.handlers ++
      PlayerApiMessages.handlers ++
      PublicQueryApiMessages.handlers ++
      ClubApiMessages.handlers ++
      OpsAnalyticsApiMessages.handlers ++
      PlatformAdminApiMessages.handlers ++
      TournamentAppealApiMessages.handlers ++
      TournamentApiMessages.handlers ++
      DictionaryApiMessages.handlers

  private val registeredContracts: Vector[ApiMessageContract] =
    AuthApiMessages.contracts ++
      PlayerApiMessages.contracts ++
      PublicQueryApiMessages.contracts ++
      ClubApiMessages.contracts ++
      OpsAnalyticsApiMessages.contracts ++
      PlatformAdminApiMessages.contracts ++
      TournamentAppealApiMessages.contracts ++
      TournamentApiMessages.contracts ++
      DictionaryApiMessages.contracts

  val handlers: Map[String, ApiMessageHandler] =
    registeredHandlers.map(handler => handler.name -> handler).toMap

  val contracts: Vector[ApiMessageContract] =
    registeredContracts

  private val handlerNames = registeredHandlers.map(_.name).toSet
  private val contractNames = registeredContracts.map(_.messageName).toSet

  require(
    handlers.size == registeredHandlers.size,
    "API message handler names must be unique"
  )
  require(
    registeredContracts.size == contractNames.size,
    "API message contract names must be unique"
  )
  require(
    handlerNames == contractNames,
    "API message contracts must match registered handlers"
  )
