package domain

type AuthorizationFailure = riichinexus.domain.service.AuthorizationFailure
type AuthenticationFailure = riichinexus.domain.service.AuthenticationFailure
type AuthorizationService = riichinexus.domain.service.AuthorizationService
type StrictRbacAuthorizationService = riichinexus.domain.service.StrictRbacAuthorizationService
type RatingChange = riichinexus.domain.service.RatingChange
type RatingService = riichinexus.domain.service.RatingService
type EloRatingConfig = riichinexus.domain.service.EloRatingConfig
type RatingConfigProvider = riichinexus.domain.service.RatingConfigProvider
type StaticRatingConfigProvider = riichinexus.domain.service.StaticRatingConfigProvider
type PlannedTable = riichinexus.domain.service.PlannedTable
type SeatingPolicy = riichinexus.domain.service.SeatingPolicy
type BalancedEloSeatingPolicy = riichinexus.domain.service.BalancedEloSeatingPolicy
type PairwiseEloRatingService = riichinexus.domain.service.PairwiseEloRatingService
type TournamentRuleEngine = riichinexus.domain.service.TournamentRuleEngine
type DefaultTournamentRuleEngine = riichinexus.domain.service.DefaultTournamentRuleEngine

val NoOpAuthorizationService: AuthorizationService =
  riichinexus.domain.service.NoOpAuthorizationService

object AuthorizationFailure:
  def apply(message: String): AuthorizationFailure =
    riichinexus.domain.service.AuthorizationFailure(message)

object AuthenticationFailure:
  def apply(
      message: String,
      code: String = "authentication_failed"
  ): AuthenticationFailure =
    riichinexus.domain.service.AuthenticationFailure(message, code)

object StrictRbacAuthorizationService:
  def apply(): StrictRbacAuthorizationService =
    new riichinexus.domain.service.StrictRbacAuthorizationService

object EloRatingConfig:
  val default: EloRatingConfig =
    riichinexus.domain.service.EloRatingConfig.default

object StaticRatingConfigProvider:
  def apply(
      config: EloRatingConfig = EloRatingConfig.default
  ): StaticRatingConfigProvider =
    riichinexus.domain.service.StaticRatingConfigProvider(config)

object PairwiseEloRatingService:
  def apply(
      configProvider: RatingConfigProvider = StaticRatingConfigProvider()
  ): PairwiseEloRatingService =
    new riichinexus.domain.service.PairwiseEloRatingService(configProvider)

object BalancedEloSeatingPolicy:
  def apply(): BalancedEloSeatingPolicy =
    new riichinexus.domain.service.BalancedEloSeatingPolicy

object DefaultTournamentRuleEngine:
  def apply(): DefaultTournamentRuleEngine =
    new riichinexus.domain.service.DefaultTournamentRuleEngine

object GlobalDictionaryRegistry:
  export riichinexus.domain.service.GlobalDictionaryRegistry.*
