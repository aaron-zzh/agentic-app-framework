<div align="center">

[![MacOS](https://github.com/xueji-dao/actormesh/workflows/MacOS/badge.svg)](https://github.com/xueji-dao/actormesh/actions)
[![Windows](https://github.com/xueji-dao/actormesh/workflows/Windows/badge.svg)](https://github.com/xueji-dao/actormesh/actions)
[![Ubuntu](https://github.com/xueji-dao/actormesh/workflows/Ubuntu/badge.svg)](https://github.com/xueji-dao/actormesh/actions)
[![Style](https://github.com/xueji-dao/actormesh/workflows/Style/badge.svg)](https://github.com/xueji-dao/actormesh/actions)
[![Install](https://github.com/xueji-dao/actormesh/workflows/Install/badge.svg)](https://github.com/xueji-dao/actormesh/actions)
[![codecov](https://codecov.io/gh/xueji-dao/actormesh/graph/badge.svg?token=Z8FQI5F98A)](https://codecov.io/gh/xueji-dao/actormesh)

</div>

# ActorMesh - Modern C++ Async Framework

一个现代 C++ 高性能异步开发框架, 待发布

## 核心特性

TODO

- **无共享设计**：线程绑定 CPU 核，拥有独占的内存、网络队列和任务队列，通过无锁消息队列实现核间通信，避免锁竞争
- **响应式编程**：异步优先，基于事件的响应式编程
- **高性能网络**: 基于 io_uring 实现高效 I/O 多路复用
- **现代 C++ 特性**：使用 C++20/23 特性提升效率与安全性 
- 添加 cpp-cli/imgui+qt界面/opencv图像处理/数据处理引擎等开发示例

## 目录结构

```bash
actormesh/
├── src/actormesh               # 库源码，源文件及头文件在一起
├── all/                        # 一键编译所有项目
├── tests/                      # 测试代码
├── examples/                   # 库开发示例
├── standalone/                 # 独立应用
├── docs/                       # vuepress + doxygen 文档
├── engines/                    # 基于本框架的引擎
├── cmake/                      # CMake 模块和配置
├── tools/                      # 系统脚本和工具
├── libs/                       # 通用库封装
├── .github/workflows/          # CI 配置
├── .clang-format               # 代码格式配置
├── .cmake-format               # CMake 格式配置
├── codecov.yaml                # 代码覆盖率配置
└── CMakeLists.txt              # 主构建文件
```

## 技术特性

| **类别**       | **功能**                                    | **价值点**                      |
| -------------- | ------------------------------------------- | ------------------------------- |
| **智能构建**   | 现代模块化 CMake 架构，CPM.cmake 依赖包管理 | 支持header-only及任意规模的项目 |
| **质量保障**   | GTest 测试框架 + Codecov 覆盖               | 测试驱动开发闭环                |
| **自动化工效** | GitHub Actions 五合一流水线                 | 构建/测试/文档/多平台自动验证   |
| **代码规范**   | 强约束格式化（clang-format + cmake-format） | 统一团队编码风格                |
| **文档体系**   | Doxygen + GitHub Pages                      | 专业文档自动生成与发布          |
| **生产级支持** | Sanitizers/静态分析器集成                   | 内存安全与代码质量双重保障      |
| **依赖管理**   | 轻量级                                      | 解决依赖地狱                    |

## 快速使用

TODO

1. 示例源码

```cpp
#include "actormesh.h"
int main() {
    return 0;
}
```

2. CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.25 FATAL_ERROR)
```

3. 编译

```bash
cmake -S all -B build -G "Ninja" -DUSE_CCACHE=1 # build-<linux|win>
cmake --build build
```

## 代码架构

TODO

### 命名空间

### 核心组件

TODO

## 参与贡献

说明 TODO

### 使用 CMake + Ninja 编译

1. vscode 界面
2. windwos + gcc 命令行
3. linux + gcc 命令行

```bash
# bash(MSYS2)
export PATH=$PATH:/c/msys64/ucrt64/bin # 临时添加环境变量 gcc
export PATH=$PATH:/f/vs/Common7/IDE/CommonExtensions/Microsoft/CMake/CMake/bin # vs 内置 cmake
export PATH=$PATH:/d/program/bin # ninja、ccache
export PATH=$PATH:/d/program/cmake-3.31.8/bin:/d/program/Git/bin # 安装的

# cmake -S . -B build -G<gen> # <gen> can be Ninja, "Unix Makefiles", XCode, "Visual Studio 15 Win64", etc.
# cmake --build build --config Release
# cmake --install ./build --prefix ./dist/ # 支持安装到指定目录
```

### 构建全部

该项目同时提供了 `all` 目录，通过 `add_subdirectory`，支持一次性构建所有目标。

1. 已安装 `ccache --version`，`ninja --version`
2. 安装 cmake-format、clang-format: `pip install clang-format==14.0.6 cmake_format==0.6.11 pyyaml`
3. 需要手动安装的依赖库：liburing、openssl, 其他可自动下载也可提前安装，参考 `cmake/ActormeshDepends.cmake`
   - 应用管理器安装：`apt install -y liburing-devel openssl`
   - 下载源码编译安装

```bash
# 开启编译缓存 -DUSE_CCACHE=ON，设置环境变量 CPM_SOURCE_CACHE(默认当前目录.cache)，需要安装 ccache
cmake -S all -B build -G "Ninja" -DUSE_CCACHE=ON # build-<linux|win>
cmake --build build

# 通过 ctest 批量运行测试，或直接运行具体测试程序
cmake --build build --target test # 或 cd build;ctest

# 格式化 需要安装 clang-format + cmake-format
cmake --build build --target format # 修改预览
cmake --build build --target check-format # 遇到错误停止
cmake --build build --target fix-format # 执行格式化

# 运行可执行程序
./build/standalone/Greeter --help
# 生成文档
cmake --build build --target GenerateDocs
```

### 编译及运行 test

```bash
cmake -S test -B build/test # -DENABLE_TEST_COVERAGE=1 测试覆盖率
cmake --build build/test
CTEST_OUTPUT_ON_FAILURE=1 cmake --build build/test --target test

# 直接运行: 
./build/test/GreeterTests
```

### 生成文档

文档会在创建[GitHub Release 版本](https://help.github.com/en/github/administering-a-repository/managing-releases-in-a-repository) 时自动构建并[发布](https://xueji-dao.github.io/actormesh)。  
若要手动构建文档执行以下命令（已安装 Doxygen, jinja2 及 Pygments）：

```bash
cmake -S docs -B build/doc
cmake --build build/doc --target GenerateDocs # 生成 doxygen 文档
cmake --build build/doc --target GenerateDocsFull # 生成 vuepress 部署的全部文档
# 预览
open build/doc/doxygen/html/index.html
```

## 注意

VS Code 通过插件 CMake Tools 并采用 Visual Studio Community 2022 Release - amd64 编译时，报错或无响应：

- [ctest] 运行 ctest 来确定可用的测试可执行文件时出错。
- 无响应：Cmake 无法执行配置，运行无响应，cmake.cleanConfigure 已完成(已返回 -1)

查看全部环境变量中是否有**中文路径**，去掉或修改


## 提交发布规范

校验工具：`@commitlint/cli` + `@commitlint/config-conventional`（确保消息合规）。
交互工具：`commitizen` + `@commitlint/cz-commitlint` + `inquirer`（降低使用门槛）。
版本管理和发布工具: semantic-release
根据提交信息自动生成版本及发行日志（github）
