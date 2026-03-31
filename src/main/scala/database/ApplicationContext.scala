package database

type ApplicationContext = riichinexus.bootstrap.ApplicationContext

object ApplicationContext:

  def fromEnvironment(
      env: collection.Map[String, String] = sys.env
  ): ApplicationContext =
    ApplicationAssembly.fromEnvironment(env)

  def inMemory(): ApplicationContext =
    ApplicationAssembly.inMemory()

  def postgres(config: DatabaseConfig): ApplicationContext =
    ApplicationAssembly.postgres(config)

  def postgres(
      config: _root_.database.postgres.DatabaseConfig
  ): ApplicationContext =
    ApplicationAssembly.postgres(config)
