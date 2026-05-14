package riichinexus.microservices.shared.api.docs

import OpenApiContractModel.*

private[docs] object OpenApiPathSpecs:
  def frontendPaths: Vector[PathSpec] =
    AuthOpenApiPathSpecs.paths ++
      ClubOpenApiPathSpecs.paths ++
      TournamentOpenApiPathSpecs.paths ++
      PublicOpenApiPathSpecs.paths ++
      TableOpenApiPathSpecs.paths ++
      AnalyticsOpenApiPathSpecs.paths
