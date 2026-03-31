package api

import cats.effect.IO

trait ApiPlan[-Input, +Output]:
  def plan(input: Input): IO[Output]
