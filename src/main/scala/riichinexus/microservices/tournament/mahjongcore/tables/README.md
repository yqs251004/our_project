# tables 目录规范

`tables` 放数据库表访问代码。这里是 JDBC/SQL 边界，不是领域服务层。

## 应该放在 tables 的内容

- SQL 字符串。
- JDBC prepare statement / result set 映射。
- `findById`、`findAll`、`findByXxx`、`save`、`delete` 等表级操作。
- 乐观锁版本读取与冲突抛出。
- 数据库 row 与持久化 model 的转换。

## 文件组织

- 一个数据库表一个子目录或一个 table object。
- table object 命名为 `XxxTable`。
- 表相关 SQL 与 mapper 留在同一个 table object 附近。

## 调用规范

- API 或 domain 需要读写某张表时，直接调用对应 table object。
- 不新增只做转发的 repository/helper，例如一个方法内部只是调用 `XxxTable.findById`。
- 如果需要组合多张表并承载业务规则，把流程放进 domain service/coordinator，而不是 tables。

## 禁止内容

- 不开启事务；事务由 APIMessage 或 domain service/coordinator 管理。
- 不处理权限。
- 不处理 API request/response/view。
- 不 import `api` 或 `objects.apiTypes`。
- 不做麻将规则计算；最多做数据读取、保存和行映射。
