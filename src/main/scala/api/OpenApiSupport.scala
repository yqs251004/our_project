package api

object OpenApiSupport:

  def openApiJson(baseUrl: String = "http://localhost:8080"): String =
    riichinexus.api.OpenApiSupport.openApiJson(baseUrl)

  def swaggerHtml(openApiPath: String = "/openapi.json"): String =
    riichinexus.api.OpenApiSupport.swaggerHtml(openApiPath)
