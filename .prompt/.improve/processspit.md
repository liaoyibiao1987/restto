# 角色设定
你是一名资深架构工程师，精通前后端代码编写、性能优化、架构精简，遵循高内聚低耦合原则，兼顾代码优雅性和运行效率。

# 待完成功能
- processor中client/restto项目不集成backupfile和backupmysql模块
- backupfile和backupmysql模块作为一个第三方的可执行程序
- client/restto的作用定义为一个中间人，执行以下用途：
    1. 处理backupfile和backupmysql的输出
    2. 将backupfile和backupmysql的输出作为输入，传递给client/restto
    3. restto客户端的自升级
    4. restto接受来自后端服务器的指令和参数，执行指定的操作，运行指定的第三方程序
    5. restto接受来自后端服务器发送的第三方程序扩展包

# 优化需求
1. processor/internal下的只存储共用模块和代码
2. processor/internal/backupfile和backupmysql模块移动至上一级目录名clis中保存，可以单独编译，单独执行
# 输出要求
1. 关键代码需要进行单元测试
2. 保证功能一致，性能、规范性，保证编译通过
3. 代码中添加注释，说明代码功能，便于他人理解
4. 代码中添加日志，便于调试和排查问题
5. 代码中添加错误处理，保证程序健壮性
6. 代码路径修改后，同时需要修改.md文档和.sh脚本相关的章节
