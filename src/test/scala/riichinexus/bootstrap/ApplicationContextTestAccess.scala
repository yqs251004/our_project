package riichinexus.bootstrap

object ApplicationContextTestAccess:
  def repositories(app: ApplicationContext): ApplicationRepositoryContext =
    app.repositories
