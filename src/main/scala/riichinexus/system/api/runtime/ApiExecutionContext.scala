package riichinexus.system.api.runtime

import riichinexus.system.postgres.JdbcConnectionFactory

/** 单次 API 执行可访问的基础设施上下文。
  *
  * 上下文保存数据库连接工厂和当前存储标签，供路由、API 消息和诊断逻辑共享同一执行环境。
  */
final case class ApiExecutionContext(
    connectionFactory: JdbcConnectionFactory,
    storageLabel: String
)
