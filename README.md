# Java 内存马查杀工具

**基于 Agent + Instrumentation + ASM 的 Java 进程内存马检测与清除工具（含 GUI）**

## 目录

- [介绍](#介绍)
- [特性](#特性)
- [核心功能](#核心功能)
- [使用方式](#使用方式)
- [安全性与风险说明](#安全性与风险说明)
- [扩展点](#开发者指南--扩展点)
- [贡献](#贡献)
- [本地测试](#本地测试)
- [联系方式](#联系方式)

---

## 介绍

本项目是一款用于检测与清除 Java 进程中内存马的工具。工具设计为三部分：

- **GUI 管理端（JFormDesigner）**：提供可视化操作界面，用于目标进程选择、会话管理、实时日志查看与分发、审计与导出。
- **Agent（注入模块）**：运行在目标 JVM 内部，基于 Instrumentation获取字节码、保存原始字节码、使用 ASM 分析字节码以判定可疑/恶意模块，并在确认后通过 redefineClasses + 反射手段执行清除。
- **部分工具类**：如socket服务端，JVM列表处理类等

GUI 与 Agent 通过 socket 连接通信：日志集中展示并按内存马类型分发到对应日志面板，便于定位与审计。

socket分别监听本地8899端口和9900端口。

---

## 特性

- 可视化管理界面（JFormDesigner）——便捷、直观的操作流程。
- Agent 注入（attach）——运行时进入目标进程执行检测与清除。
- 字节码持久化保存——把抓取的内存马字节码 写入本地，便于离线反编译与深度分析。
- 基于 ASM 的字节码分析引擎——按规则/签名识别可疑行为模式。
- 一键清除（Instrumentation.redefineClasses() + 反射移除入口）——尽可能彻底移除内存马入口与持久化引用。
- 实时日志分发与导出——按模块/类型分发日志并支持导出 JSON/HTML 报告。
- 可扩展的检测规则库——便于添加新型内存马特征与策略。

---

## 核心功能

1. **注入与会话管理**

   - 使用 Attach API 将 Agent 加载到目标 JVM，并建立 socket 会话。
   - 支持通过 PID 列表选择或手动指定目标进程。

2. **字节码抓取与保存**

   - 在目标进程内抓取内存马字节码。
   - 将抓取到的字节码持久化到磁盘（按包/类名组织），便于使用 JADX /IDEA 等工具反编译。

3. **字节码分析（ASM）**

   - 解析方法调用、反射使用、动态类生成、独立 ClassLoader 等可疑特征。
   - 根据规则/签名对可疑模块进行分类（例如：动态加载并执行字节数组的模块、持有网络回连逻辑的模块、注入到关键框架的 hook 等）。

4. **清除逻辑**

   - 对确认的内存马使用 Instrumentation.redefineClasses()将恶意类替换为安全/空实现。
   - 通过反射断开入口（清理静态字段、移除 hook、停止相关线程、关闭网络连接等）。
   - 清除操作在进程内执行并记录完整操作日志以备审计。

5. **日志与告警**
   - 所有检测与清除操作实时回传 GUI 并按模块分发到相应日志面板。
   - 支持标记高危事件为告警并导出审计报告。

---

## 使用方式

> **重要提醒**：本工具仅用于在**已获得授权**的环境中进行安全检测与应急响应。未经授权在第三方系统使用可能违法或违反政策。

### 系统与依赖

- 建议 JDK：JDK 8 
- 构建工具：Maven 
- 依赖：ASM、JFormDesigner 生成的 GUI 代码

### 构建

```bash
# 使用 Maven
mvn clean package
```

**JAR构建**

项目结构->工件

<img width="1026" height="861" alt="image-20250811153645528" src="https://github.com/user-attachments/assets/09e010dd-203d-422f-ad8e-034c98dfff44" />


构建工件

<img width="375" height="220" alt="image-20250811153806724" src="https://github.com/user-attachments/assets/13f922b5-7a29-4fdf-9b75-f4e771dd57d2" />


Agent端

<img width="602" height="472" alt="image-20250811153926656" src="https://github.com/user-attachments/assets/83255a13-f820-4b97-8262-39522ed72887" />


修改gui.java 149行路径代码

<img width="834" height="88" alt="image-20250811154020820" src="https://github.com/user-attachments/assets/22a5f100-e18f-4d14-8988-0fa7055010fe" />


### 启动 GUI

将Kill_Memshell.jar文件将MemShellScannerAgent-1.0-SNAPSHOT.jar文件放到同一目录下

```bash
java -jar MemShellScannerAgent-1.0-SNAPSHOT.jar
```
<img width="774" height="497" alt="image-20250811155714623" src="https://github.com/user-attachments/assets/b528c487-e799-4e11-9fe8-e37ebd0291f9" />



### 扫描与清除

1. 启动 GUI 点击获取当前进程，可得到当前运行的JVM进程列表。
2. 选择进程点击attach则可注入进程，下方的日志界面会输出检测进程和结果。在中间的内存马分类板块会输出各内存马相关信息。
3. 在内存马板块获取到内存马类名后可在右侧板块按照格式[filter/servlet/listener....]classname 输入，点击清除内存马，即可移除进程中内存马。
4. 导出的内存马字节码保存在当前根目录下的dumpClassFile文件夹中

### 日志与导出

- 支持导出抓取到的字节码（打包为 zip）以便离线反编译分析。
- 支持将检测报告导出为 JSON 或 HTML 以便存档与审计。

---

## 安全性与风险说明

- **权限**：Attach 与类重定义通常需要对目标机器/进程有相应权限（例如 root / 管理员 / 进程所有者）。
- **审计**：所有清除操作应保留完整日志与抓取的字节码备份，以便回溯与复现。
- **合法性**：请在获得明确授权的前提下操作；未经授权的入侵检测与清除可能违法。

---

## 扩展点

- 扩展 ASM 规则库以覆盖更多内存马变种。
- 增加“清除策略模板”并支持回滚机制。
- 引入沙箱机制，在隔离的 JVM 中先执行清除试运行。
- 集成自动化反编译与相似签名比对（如 yara-like 签名库）。
- 提供远程集中管理与多 Agent 会话汇总功能。

---

## 本地测试
本地测试环境：https://github.com/CafeD1/MemshellCode

## 贡献

欢迎提交 Issue 与 PR。提交贡献时请包含：

- 复现步骤与最小测试用例。
- 日志与错误栈。
- 对检测规则或清除策略的回归测试用例。

---

## 联系方式

维护者：cafedi



