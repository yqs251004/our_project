package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport
import riichinexus.microservices.opsanalytics.api.responses.*
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

private[opsanalytics] trait DemoScenarioBootstrapSupport extends DemoScenarioSupport:
  def bootstrapBasicScenario(refreshDerived: Boolean = true): DemoScenarioSnapshot =
    bootstrapScenario(DemoScenarioVariant.Basic, refreshDerived)

  def bootstrapScenario(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      refreshDerived: Boolean = true
  ): DemoScenarioSnapshot =
    val config = scenarioConfig(variant)
    val players = PlayerSeeds.map(seed =>
      playerService.registerPlayer(
        userId = seed.userId,
        nickname = seed.nickname,
        rank = seed.rank,
        registeredAt = SeededAt,
        initialElo = seed.initialElo
      )
    )

    val playerByUserId = players.map(player => player.userId -> player).toMap
    val alice = playerByUserId("demo-alice")
    val bob = playerByUserId("demo-bob")
    val charlie = playerByUserId("demo-charlie")
    val diana = playerByUserId("demo-diana")
    val eve = playerByUserId("demo-eve")
    val frank = playerByUserId("demo-frank")
    val grace = playerByUserId("demo-grace")
    val heidi = playerByUserId("demo-heidi")

    ensureSuperAdmin(alice.id)

    val eastWindClub = clubService.createClub(
      name = "EastWind Club",
      creatorId = alice.id,
      createdAt = SeededAt,
      actor = principalFor(alice.id)
    )
    ensureClubMember(eastWindClub.id, bob.id, alice.id)
    ensureClubMember(eastWindClub.id, charlie.id, alice.id)

    val southWindClub = clubService.createClub(
      name = "SouthWind Club",
      creatorId = eve.id,
      createdAt = SeededAt,
      actor = principalFor(eve.id)
    )
    ensureClubMember(southWindClub.id, frank.id, eve.id)
    ensureClubMember(southWindClub.id, grace.id, eve.id)

    val stage = TournamentStage(
      id = config.stageId,
      name = config.stageName,
      format = StageFormat.Swiss,
      order = 1,
      roundCount = 1
    )
    val tournament = tournamentService.createTournament(
      name = config.tournamentName,
      organizer = config.organizer,
      startsAt = SeededAt.plusSeconds(3600),
      endsAt = SeededAt.plusSeconds(10800),
      stages = Vector(stage),
      adminId = Some(alice.id),
      actor = AccessPrincipal.system
    )

    val admin = principalFor(alice.id)
    ensureTournamentClub(tournament.id, eastWindClub.id, admin)
    ensureTournamentClub(tournament.id, southWindClub.id, admin)
    ensureTournamentPlayer(tournament.id, diana.id, admin)
    ensureTournamentPlayer(tournament.id, heidi.id, admin)
    ensureTournamentPublished(tournament.id, admin)
    ensureTournamentStarted(tournament.id, admin)

    val tables = tournamentService.scheduleStageTables(tournament.id, config.stageId, admin)
    applyVariantScenario(config, tournament.id, config.stageId, tables, admin)
    ensureDemoGuestSession(config)
    if refreshDerived then flushDerivedViews()

    val refreshedTournament = tournamentRepository.findById(tournament.id).getOrElse(tournament)
    val refreshedStage = refreshedTournament.stages.find(_.id == config.stageId).getOrElse(stage)
    buildScenarioSnapshot(config, alice.id, refreshedTournament, refreshedStage)

  protected def findScenario(
      variant: DemoScenarioVariant
  ): Option[(DemoScenarioConfig, PlayerId, Tournament, TournamentStage)] =
    val config = scenarioConfig(variant)
    for
      alice <- playerRepository.findByUserId("demo-alice")
      tournament <- tournamentRepository.findByNameAndOrganizer(config.tournamentName, config.organizer)
      stage <- tournament.stages.find(_.id == config.stageId)
    yield (config, alice.id, tournament, stage)

  private def ensureSuperAdmin(playerId: PlayerId): Player =
    val player = playerRepository.findById(playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    if player.roleGrants.exists(_.role == RoleKind.SuperAdmin) then player
    else playerRepository.save(player.grantRole(RoleGrant.superAdmin(SeededAt, None)))

  protected def principalFor(playerId: PlayerId): AccessPrincipal =
    val player = playerRepository.findById(playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    AccessPrincipal(
      principalId = player.id.value,
      displayName = player.nickname,
      playerId = Some(player.id),
      roleGrants = player.roleGrants
    )

  private def ensureClubMember(clubId: ClubId, playerId: PlayerId, operatorId: PlayerId): Unit =
    val club = clubRepository.findById(clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    if !club.members.contains(playerId) then
      clubService.addMember(clubId, playerId, principalFor(operatorId))
      ()

  private def ensureTournamentClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  ): Unit =
    val tournament = tournamentRepository.findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    if !tournament.participatingClubs.contains(clubId) then
      tournamentService.registerClub(tournamentId, clubId, actor)
      ()

  private def ensureTournamentPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Unit =
    val tournament = tournamentRepository.findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    if !tournament.participatingPlayers.contains(playerId) then
      tournamentService.registerPlayer(tournamentId, playerId, actor)
      ()

  private def ensureTournamentPublished(
      tournamentId: TournamentId,
      actor: AccessPrincipal
  ): Unit =
    tournamentRepository.findById(tournamentId).foreach { tournament =>
      if tournament.status == TournamentStatus.Draft then
        tournamentService.publishTournament(tournamentId, actor)
        ()
    }

  private def ensureTournamentStarted(
      tournamentId: TournamentId,
      actor: AccessPrincipal
  ): Unit =
    tournamentRepository.findById(tournamentId).foreach { tournament =>
      if tournament.status == TournamentStatus.RegistrationOpen || tournament.status == TournamentStatus.Scheduled then
        tournamentService.startTournament(tournamentId, actor)
        ()
    }

  private def applyVariantScenario(
      config: DemoScenarioConfig,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      tables: Vector[Table],
      actor: AccessPrincipal
  ): Unit =
    config.variant match
      case DemoScenarioVariant.Basic =>
        seedArchivedTablesIfNeeded(tournamentId, stageId, tables, archiveCount = 1, actor = actor)
      case DemoScenarioVariant.Leaderboard =>
        seedArchivedTablesIfNeeded(tournamentId, stageId, tables, archiveCount = 2, actor = actor)
      case DemoScenarioVariant.Appeal =>
        seedArchivedTablesIfNeeded(tournamentId, stageId, tables, archiveCount = 1, actor = actor)
        ensureOpenAppealScenario(tournamentId, stageId, actor)

  private def seedArchivedTablesIfNeeded(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      tables: Vector[Table],
      archiveCount: Int,
      actor: AccessPrincipal
  ): Unit =
    val stageTables =
      if tables.nonEmpty then tables
      else tableRepository.findByTournamentAndStage(tournamentId, stageId)
    stageTables
      .sortBy(table => (table.tableNo, table.id.value))
      .take(math.max(1, archiveCount))
      .foreach { table =>
      if matchRecordRepository.findByTable(table.id).isEmpty then
        val readyTable =
          if table.status == TableStatus.Pending then
            tableService.startTable(table.id, SeededAt.plusSeconds(3900), actor).getOrElse(table)
          else table
        tableService.recordCompletedTable(
          readyTable.id,
          demoPaifu(
            readyTable,
            tournamentId,
            stageId,
            SeededAt.plusSeconds(5400 + (table.tableNo.toLong - 1L) * 900L)
          ),
          actor
        )
        ()
    }

  private def ensureOpenAppealScenario(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal
  ): Unit =
    tableRepository.findByTournamentAndStage(tournamentId, stageId)
      .sortBy(table => (table.tableNo, table.id.value))
      .find(table => table.status != TableStatus.Archived)
      .foreach { table =>
        val existingAppeal = appealTicketRepository.findAll().find(ticket =>
          ticket.tableId == table.id &&
            (ticket.status == AppealStatus.Open ||
              ticket.status == AppealStatus.UnderReview ||
              ticket.status == AppealStatus.Escalated)
        )
        if existingAppeal.isEmpty then
          val openedBy = table.seats.head.playerId
          appealService.fileAppeal(
            tableId = table.id,
            openedBy = openedBy,
            description = "Demo appeal: player reported an abnormal table condition for moderator review.",
            priority = AppealPriority.High,
            dueAt = Some(SeededAt.plusSeconds(7200)),
            actor = principalFor(openedBy),
            createdAt = SeededAt.plusSeconds(6300)
          )
          ()
      }

  private def ensureDemoGuestSession(config: DemoScenarioConfig): GuestAccessSession =
    guestSessionRepository.findAll()
      .find(_.displayName == config.guestDisplayName)
      .getOrElse(
        guestSessionService.createSession(
          displayName = config.guestDisplayName,
          ttl = java.time.Duration.ofDays(30),
          createdAt = SeededAt
        )
      )

  protected def demoPaifu(
      table: Table,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      recordedAt: Instant
  ): Paifu =
    val seatByWind = table.seats.map(seat => seat.seat -> seat.playerId).toMap
    val east = seatByWind(SeatWind.East)
    val south = seatByWind(SeatWind.South)
    val west = seatByWind(SeatWind.West)
    val north = seatByWind(SeatWind.North)

    val firstRound = KyokuRecord(
      descriptor = KyokuDescriptor(SeatWind.East, handNumber = 1),
      initialHands = table.seats.map(seat => seat.playerId -> Vector("1m", "2m", "3m")).toMap,
      actions = Vector(
        PaifuAction(1, Some(east), PaifuActionType.Draw, Some("4m"), Some(3)),
        PaifuAction(2, Some(east), PaifuActionType.Discard, Some("9p"), Some(2)),
        PaifuAction(3, Some(south), PaifuActionType.Riichi, note = Some("closed riichi")),
        PaifuAction(4, Some(south), PaifuActionType.Discard, Some("5s"), Some(1)),
        PaifuAction(5, Some(south), PaifuActionType.Win, Some("3p"), Some(0))
      ),
      result = AgariResult(
        outcome = HandOutcome.Ron,
        winner = Some(south),
        target = Some(west),
        han = Some(3),
        fu = Some(40),
        yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Ippatsu", 1)),
        points = 7700,
        scoreChanges = Vector(
          ScoreChange(east, 0),
          ScoreChange(south, 7700),
          ScoreChange(west, -7700),
          ScoreChange(north, 0)
        )
      )
    )

    val secondRound = KyokuRecord(
      descriptor = KyokuDescriptor(SeatWind.East, handNumber = 2),
      initialHands = table.seats.map(seat => seat.playerId -> Vector("4p", "5p", "6p")).toMap,
      actions = Vector(
        PaifuAction(1, Some(north), PaifuActionType.Draw, Some("7m"), Some(2)),
        PaifuAction(2, Some(north), PaifuActionType.Discard, Some("7m"), Some(2)),
        PaifuAction(3, Some(east), PaifuActionType.Riichi, note = Some("pressure riichi")),
        PaifuAction(4, Some(east), PaifuActionType.Win, Some("2s"), Some(0))
      ),
      result = AgariResult(
        outcome = HandOutcome.Tsumo,
        winner = Some(east),
        target = None,
        han = Some(2),
        fu = Some(30),
        yaku = Vector(Yaku("Riichi", 1), Yaku("Tsumo", 1)),
        points = 2000,
        scoreChanges = Vector(
          ScoreChange(east, 4000),
          ScoreChange(south, -1000),
          ScoreChange(west, -1000),
          ScoreChange(north, -2000)
        )
      )
    )

    Paifu(
      id = IdGenerator.paifuId(),
      metadata = PaifuMetadata(
        recordedAt = recordedAt,
        source = "demo-seed",
        tableId = table.id,
        tournamentId = tournamentId,
        stageId = stageId,
        seats = table.seats
      ),
      rounds = Vector(firstRound, secondRound),
      finalStandings = Vector(
        FinalStanding(south, SeatWind.South, 31700, 1),
        FinalStanding(east, SeatWind.East, 29000, 2),
        FinalStanding(north, SeatWind.North, 23000, 3),
        FinalStanding(west, SeatWind.West, 16300, 4)
      )
    )
