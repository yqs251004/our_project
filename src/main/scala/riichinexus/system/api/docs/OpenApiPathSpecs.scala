package riichinexus.system.api.docs

import OpenApiContractModel.*

object OpenApiPathSpecs:
  def frontendPaths: Vector[PathSpec] =
    AuthOpenApiPathSpecs.paths ++
      ClubOpenApiPathSpecs.paths ++
      PublicOpenApiPathSpecs.paths ++
      AnalyticsOpenApiPathSpecs.paths
