// agentSpawn hook: architect 启动时打印欢迎信息给用户
// 非零退出码 + stderr → 才会显示 warning 给用户
process.stderr.write(`🏗️ 架构师已就位。这是一个 hooks 测试\n`);
process.exit(1);
