package org.cafedi.detect;
/**
 * Controller检测器。
 * 参数说明：
 *   - inst: Instrumentation（必须）
 *   - writer: 日志输出
 *   - tryUnregister: 是否尝试反注册 RequestMapping（可选，若失败只记录）
 *   - tryUnload: 是否尝试调用 ClassUnloader 卸载/空壳替换（需你提供 ClassUnloader 实现并在 classpath）
 */
public class ControllerInspector {

}
