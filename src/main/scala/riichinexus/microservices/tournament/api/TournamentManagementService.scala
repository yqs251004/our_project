package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport

private[tournament] trait TournamentManagementWorkflow extends TournamentWorkflowSupport:
  def createTournament(
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      stages: Vector[TournamentStage],
      adminId: Option[PlayerId] = None,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Tournament =
    transactionManager.inTransaction {
      require(name.trim.nonEmpty, "Tournament name cannot be empty")
      require(organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
      require(startsAt.isBefore(endsAt), "Tournament start time must be earlier than end time")

      val dictionarySnapshot = RuntimeDictionarySupport.snapshot(globalDictionaryRepository)
      val normalizedStages = TournamentDefaults.initialStages(stages)
        .map(stage => normalizeStage(stage, dictionarySnapshot))
        .sortBy(_.order)
      requireUniqueStageConfiguration(normalizedStages)

      adminId.foreach { targetAdminId =>
        val adminPlayer = playerRepository
          .findById(targetAdminId)
          .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
        requireActivePlayer(adminPlayer, s"Player ${targetAdminId.value} cannot administer tournaments")
      }

      val tournament = tournamentRepository.findByNameAndOrganizer(name, organizer) match
        case Some(existing) =>
          existing.copy(
            startsAt = startsAt,
            endsAt = endsAt,
            stages = normalizedStages
          )
        case None =>
          Tournament(
            id = IdGenerator.tournamentId(),
            name = name,
            organizer = organizer,
            startsAt = startsAt,
            endsAt = endsAt,
            admins = adminId.toVector,
            stages = normalizedStages
          )

      adminId.foreach { targetAdminId =>
        playerRepository.findById(targetAdminId).foreach { adminPlayer =>
          playerRepository.save(
            adminPlayer.grantRole(
              RoleGrant.tournamentAdmin(tournament.id, startsAt, actor.playerId)
            )
          )
        }
      }

      tournamentRepository.save(
        adminId.fold(tournament)(tournament.assignAdmin)
      )
    }

  def registerPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      playerRepository
        .findById(playerId)
        .map { player =>
          requireActivePlayer(player, s"Player ${playerId.value} cannot enter tournament ${tournamentId.value}")
        }
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerPlayer(playerId))
      }
    }

  def registerClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      clubRepository
        .findById(clubId)
        .map(ensureClubActive)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerClub(clubId))
      }
    }

  def acceptClubParticipation(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      val club = clubRepository
        .findById(clubId)
        .map { club =>
          ensureClubActive(club)
          club
        }
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
      requireClubLineupCapability(actor, club)

      tournamentRepository.findById(tournamentId).map { tournament =>
        val alreadyParticipating = tournament.participatingClubs.contains(clubId)
        val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(clubId))
        if !alreadyParticipating && !isWhitelisted then
          throw IllegalArgumentException(
            s"Club ${clubId.value} is not invited to tournament ${tournamentId.value}"
          )
        tournamentRepository.save(tournament.registerClub(clubId))
      }
    }

  def declineClubParticipation(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      val club = clubRepository
        .findById(clubId)
        .map { club =>
          ensureClubActive(club)
          club
        }
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
      requireClubLineupCapability(actor, club)

      tournamentRepository.findById(tournamentId).map { tournament =>
        val trackedParticipation =
          tournament.participatingClubs.contains(clubId) || tournament.whitelist.exists(_.clubId.contains(clubId))
        if !trackedParticipation then
          throw IllegalArgumentException(
            s"Club ${clubId.value} is not participating in tournament ${tournamentId.value}"
          )
        tournamentRepository.save(tournament.removeClub(clubId))
      }
    }

  def removeClubParticipation(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      clubRepository
        .findById(clubId)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        val trackedParticipation =
          tournament.participatingClubs.contains(clubId) || tournament.whitelist.exists(_.clubId.contains(clubId))
        if !trackedParticipation then
          throw IllegalArgumentException(
            s"Club ${clubId.value} is not participating in tournament ${tournamentId.value}"
          )
        tournamentRepository.save(tournament.removeClub(clubId))
      }
    }

  def whitelistPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      playerRepository
        .findById(playerId)
        .map { player =>
          requireActivePlayer(player, s"Player ${playerId.value} cannot be whitelisted")
        }
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistPlayer(playerId))
      }
    }

  def whitelistClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      clubRepository
        .findById(clubId)
        .map(ensureClubActive)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistClub(clubId))
      }
    }

  def publishTournament(
      tournamentId: TournamentId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        if tournament.stages.isEmpty then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} cannot be published without stages"
          )
        tournamentRepository.save(tournament.publish)
      }
    }

  def startTournament(
      tournamentId: TournamentId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        if tournament.participatingPlayers.isEmpty && tournament.participatingClubs.isEmpty then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} cannot start without participants"
          )
        tournamentRepository.save(tournament.start)
      }
    }

  def assignTournamentAdmin(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Tournament] =
    transactionManager.inTransaction {
      for
        tournament <- tournamentRepository.findById(tournamentId)
        player <- playerRepository.findById(playerId)
      yield
        requireActivePlayer(player, s"Player ${playerId.value} cannot be granted tournament admin")
        authorizationService.requirePermission(
          actor,
          Permission.AssignTournamentAdmin,
          tournamentId = Some(tournamentId)
        )

        playerRepository.save(
          player.grantRole(
            RoleGrant.tournamentAdmin(tournamentId, grantedAt, actor.playerId)
          )
        )
        val updatedTournament = tournamentRepository.save(tournament.assignAdmin(playerId))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "tournament",
            aggregateId = tournamentId.value,
            eventType = "TournamentAdminAssigned",
            occurredAt = grantedAt,
            actorId = actor.playerId,
            details = Map("playerId" -> playerId.value),
            note = Some(s"Granted tournament admin to ${playerId.value}")
          )
        )
        updatedTournament
    }

  def revokeTournamentAdmin(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      for
        tournament <- tournamentRepository.findById(tournamentId)
        player <- playerRepository.findById(playerId)
      yield
        authorizationService.requirePermission(
          actor,
          Permission.AssignTournamentAdmin,
          tournamentId = Some(tournamentId)
        )

        if !tournament.admins.contains(playerId) then
          throw IllegalArgumentException(
            s"Player ${playerId.value} is not a tournament admin of tournament ${tournamentId.value}"
          )

        if tournament.admins.size <= 1 then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} must retain at least one tournament admin"
          )

        playerRepository.save(player.revokeTournamentAdmin(tournamentId))
        val updatedTournament = tournamentRepository.save(
          tournament.copy(admins = tournament.admins.filterNot(_ == playerId))
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "tournament",
            aggregateId = tournamentId.value,
            eventType = "TournamentAdminRevoked",
            occurredAt = Instant.now(),
            actorId = actor.playerId,
            details = Map("playerId" -> playerId.value),
            note = Some(s"Revoked tournament admin from ${playerId.value}")
          )
        )
        updatedTournament
    }

