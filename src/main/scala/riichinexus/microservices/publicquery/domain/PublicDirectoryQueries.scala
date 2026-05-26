package riichinexus.microservices.publicquery.domain

import java.sql.Connection

import riichinexus.domain.model.*
import riichinexus.microservices.dictionary.objects.GlobalDictionaryEntry
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.dictionary.tables.globaldictionary.GlobalDictionaryTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.tournament.domain.StageLineupSupport
import riichinexus.microservices.tournament.objects.RankSnapshotView
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

object PublicDirectoryQueries:
  def publicSchedules(connection: Connection): Vector[PublicScheduleView] =
    val tournaments = TournamentTable.findPublic(connection)
    val lineupPlayersById = PlayerTable
      .findByIds(connection, tournaments.flatMap(_.stages.flatMap(_.lineupSubmissions.flatMap(_.seats.map(_.playerId)))).distinct)
      .map(player => player.id -> player)
      .toMap
    val tablesByStage = TournamentGameTable.findByTournamentIds(connection, tournaments.map(_.id))
      .groupBy(table => table.tournamentId -> table.stageId)
      .withDefaultValue(Vector.empty)
    val clubsById = ClubTable.findByIds(connection, tournaments.flatMap(_.participatingClubs).distinct)
      .map(club => club.id -> club)
      .toMap

    tournaments.flatMap { tournament =>
      tournament.stages.map { stage =>
        val stageTables = tablesByStage(tournament.id -> stage.id)
        val activeTableCount = stageTables.count(table =>
          table.status != TableStatus.Archived
        )
        val lineupPlayers = StageLineupSupport.resolveEligiblePlayers(stage, lineupPlayersById.get)
        val fallbackClubMembers = tournament.participatingClubs.flatMap { clubId =>
          clubsById.get(clubId).toVector.flatMap(_.members)
        }
        PublicScheduleView(
          tournamentId = tournament.id.value,
          tournamentName = tournament.name,
          tournamentStatus = tournament.status.toString,
          stageId = stage.id.value,
          stageName = stage.name,
          stageStatus = stage.status.toString,
          currentRound = stage.currentRound,
          roundCount = stage.roundCount,
          startsAt = tournament.startsAt.toString,
          endsAt = tournament.endsAt.toString,
          tableCount = stageTables.size,
          activeTableCount = activeTableCount,
          pendingTablePlanCount = stage.pendingTablePlans.size,
          participantCount = (lineupPlayers ++ tournament.participatingPlayers ++ fallbackClubMembers).distinct.size,
          whitelistCount = tournament.whitelist.size
        )
      }
    }

  def publicClubDirectory(connection: Connection): Vector[PublicClubDirectoryEntry] =
    val clubs = ClubTable.findActive(connection).sortBy(_.name)
    val playersById = PlayerTable
      .findByIds(connection, clubs.flatMap(_.members).distinct)
      .map(player => player.id -> player)
      .toMap
    val activeClubIds = clubs.map(_.id).toSet
    val relatedClubsById = ClubTable.findByIds(
      connection,
      clubs.flatMap(_.relations.map(_.targetClubId)).distinct.filterNot(activeClubIds.contains)
    ).map(club => club.id -> club).toMap
    val clubsById = clubs.map(club => club.id -> club).toMap ++ relatedClubsById

    clubs.map { club =>
      val activeMemberCount = club.members.count(playerId =>
        playersById.get(playerId).exists(_.status == PlayerStatus.Active)
      )
      val rivalryTargets = club.relations.filter(_.relation == ClubRelationKind.Rivalry)
      val strongestRival = rivalryTargets
        .flatMap(relation => clubsById.get(relation.targetClubId))
        .sortBy(rival => (-rival.powerRating, rival.name))
        .headOption
      PublicClubDirectoryEntry(
        clubId = club.id,
        name = club.name,
        memberCount = club.members.size,
        activeMemberCount = activeMemberCount,
        adminCount = club.admins.size,
        powerRating = round2(club.powerRating),
        totalPoints = club.totalPoints,
        treasuryBalance = club.treasuryBalance,
        pointPool = club.pointPool,
        allianceCount = club.relations.count(_.relation == ClubRelationKind.Alliance),
        rivalryCount = rivalryTargets.size,
        strongestRivalClubId = strongestRival.map(_.id),
        strongestRivalPower = strongestRival.map(rival => round2(rival.powerRating)),
        honorTitles = club.honors.map(_.title).sorted,
        relations = club.relations.map(PublicClubRelationView.fromDomain)
      )
    }

  def publicPlayerLeaderboard(connection: Connection, limit: Int = 100): Vector[PlayerLeaderboardEntry] =
    val dictionarySnapshot = PublicRankDictionarySupport.snapshot(GlobalDictionaryTable.findAll(connection))
    PlayerTable.findAll(connection)
      .map(player => player -> PublicRankDictionarySupport.normalizeRank(player.currentRank, dictionarySnapshot))
      .sortBy { case (player, normalizedRank) =>
        val normalizedRankScore = normalizedRank.map(_.score).getOrElse(Int.MinValue)
        (-player.elo, -normalizedRankScore, player.nickname)
      }
      .take(limit)
      .map { case (player, normalizedRank) =>
        PlayerLeaderboardEntry(
          playerId = player.id,
          nickname = player.nickname,
          elo = player.elo,
          currentRank = RankSnapshotView.fromDomain(player.currentRank),
          normalizedRankScore = normalizedRank.map(_.score),
          clubIds = player.boundClubIds,
          status = player.status
        )
      }

  def publicClubLeaderboard(connection: Connection, limit: Int = 100): Vector[ClubLeaderboardEntry] =
    ClubTable.findActive(connection)
      .sortBy(club => (-club.powerRating, -club.totalPoints, club.name))
      .take(limit)
      .map { club =>
        ClubLeaderboardEntry(
          clubId = club.id,
          name = club.name,
          powerRating = round2(club.powerRating),
          totalPoints = club.totalPoints,
          memberCount = club.members.size
        )
      }

  def listPublicTournaments(
      connection: Connection,
      status: Option[TournamentStatus],
      organizer: Option[String]
  ): Vector[Tournament] =
    TournamentTable.findFiltered(
      connection = connection,
      status = status,
      organizer = organizer,
      includeDraft = false
    )

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

private object PublicRankDictionarySupport:
  private val RankNormalizationPrefix = GlobalDictionaryRegistry.RankNormalizationPrefix

  final case class NormalizedRank(
      score: Int,
      sourceKey: String
  )

  final case class DictionarySnapshot(
      valuesByNormalizedKey: Map[String, String]
  )

  def snapshot(
      entries: Vector[GlobalDictionaryEntry]
  ): DictionarySnapshot =
    DictionarySnapshot(
      entries
        .iterator
        .map(entry => normalizedKey(entry.key) -> entry.value.trim)
        .filter(_._2.nonEmpty)
        .toMap
    )

  def normalizeRank(
      rank: RankSnapshot,
      dictionarySnapshot: DictionarySnapshot
  ): Option[NormalizedRank] =
    val platformKey = GlobalDictionaryRegistry.normalizePlatformKey(rank.platform)
    val normalizedTier = GlobalDictionaryRegistry.normalizedToken(rank.tier)
    val starSpecificKeys =
      rank.stars.toVector.flatMap { stars =>
        Vector(
          s"$RankNormalizationPrefix$platformKey.$normalizedTier.$stars",
          s"$RankNormalizationPrefix$platformKey.$normalizedTier-$stars"
        )
      }
    val baseKey = s"$RankNormalizationPrefix$platformKey.$normalizedTier"

    starSpecificKeys
      .flatMap(key => readInt(dictionarySnapshot, key).map(score => NormalizedRank(score, key)))
      .headOption
      .orElse {
        readInt(dictionarySnapshot, baseKey).map { base =>
          val weightedScore = rank.stars.flatMap { stars =>
            readInt(dictionarySnapshot, s"$RankNormalizationPrefix$platformKey.starweight")
              .map(starWeight => base + stars * starWeight)
          }.getOrElse(base)

          NormalizedRank(
            score = weightedScore,
            sourceKey = if rank.stars.nonEmpty then s"$RankNormalizationPrefix$platformKey.starweight" else baseKey
          )
        }
      }

  private def readInt(
      dictionarySnapshot: DictionarySnapshot,
      key: String
  ): Option[Int] =
    dictionarySnapshot.valuesByNormalizedKey.get(normalizedKey(key)).flatMap(parseInt)

  private def parseInt(value: String): Option[Int] =
    scala.util.Try(value.trim.toInt).toOption

  private def normalizedKey(key: String): String =
    GlobalDictionaryRegistry.normalizeKey(key)
