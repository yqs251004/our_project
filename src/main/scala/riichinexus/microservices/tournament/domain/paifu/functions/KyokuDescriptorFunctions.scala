package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifu.KyokuDescriptor

/** KyokuDescriptorFunctions 提供KyokuDescriptor相关的领域计算、校验和转换函数。 */

private[tournament] object KyokuDescriptorFunctions:
  def validate(descriptor: KyokuDescriptor): Unit =
    require(descriptor.handNumber >= 1 && descriptor.handNumber <= 4, "Hand number must be between 1 and 4")
    require(descriptor.honba >= 0, "Honba must be non-negative")
