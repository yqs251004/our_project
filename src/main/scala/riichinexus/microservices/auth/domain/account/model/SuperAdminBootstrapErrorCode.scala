package riichinexus.microservices.auth.domain.account.model

/** 超级管理员初始化流程返回给客户端的稳定错误码。
  *
  * 这些取值用于前端映射本地化提示，避免依赖后端自然语言错误文案。
  */
private[auth] enum SuperAdminBootstrapErrorCode:
  case InvalidBootstrapKey
  case BootstrapKeyNotConfigured
  case AlreadyInitialized
  case PasswordTooShort
  case UsernameAlreadyRegistered
  case DisplayNameRequired
  case UsernameRequired

object SuperAdminBootstrapErrorCode:
  def toString(errorCode: SuperAdminBootstrapErrorCode): String =
    errorCode match
      case SuperAdminBootstrapErrorCode.InvalidBootstrapKey =>
        "invalid_bootstrap_key"
      case SuperAdminBootstrapErrorCode.BootstrapKeyNotConfigured =>
        "superadmin_bootstrap_key_not_configured"
      case SuperAdminBootstrapErrorCode.AlreadyInitialized =>
        "superadmin_already_initialized"
      case SuperAdminBootstrapErrorCode.PasswordTooShort =>
        "password_too_short"
      case SuperAdminBootstrapErrorCode.UsernameAlreadyRegistered =>
        "username_already_registered"
      case SuperAdminBootstrapErrorCode.DisplayNameRequired =>
        "display_name_required"
      case SuperAdminBootstrapErrorCode.UsernameRequired =>
        "username_required"

  def fromString(value: String): SuperAdminBootstrapErrorCode =
    value match
      case "invalid_bootstrap_key" =>
        SuperAdminBootstrapErrorCode.InvalidBootstrapKey
      case "superadmin_bootstrap_key_not_configured" =>
        SuperAdminBootstrapErrorCode.BootstrapKeyNotConfigured
      case "superadmin_already_initialized" =>
        SuperAdminBootstrapErrorCode.AlreadyInitialized
      case "password_too_short" =>
        SuperAdminBootstrapErrorCode.PasswordTooShort
      case "username_already_registered" =>
        SuperAdminBootstrapErrorCode.UsernameAlreadyRegistered
      case "display_name_required" =>
        SuperAdminBootstrapErrorCode.DisplayNameRequired
      case "username_required" =>
        SuperAdminBootstrapErrorCode.UsernameRequired
      case other =>
        throw IllegalArgumentException(s"Unknown super admin bootstrap error code: $other")
