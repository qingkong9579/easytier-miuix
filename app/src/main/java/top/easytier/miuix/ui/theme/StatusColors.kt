package top.easytier.miuix.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 语义色 token。
 *
 * 规范要求屏幕里不出现裸 `Color(0xFF...)`：颜色只能来自
 * `MiuixTheme.colorScheme.*` 或这里。此前 `TrafficChart` 的默认值与
 * `StatusScreen` 的 `TX_COLOR`/`RX_COLOR` 各自写了一份相同的色值，改一处
 * 就会分叉；状态色（绿/橙/红）也散落在 6 处 `if/when` 分支里，且是同
 * 一个 `when` 表达式里混用 `MiuixTheme.colorScheme.error` 与硬编码。
 *
 * 这里把「随主题变化」的语义色做成 [Composable] getter（跟随深色模式），
 * 只有流量图的两条曲线用固定品牌色——它们是数据可视化标识色，需要跨主题
 * 保持可辨识，但已收敛到唯一出处。
 */
object StatusColors {

    /** 运行中 / 健康 / 低延迟 / 无丢包。 */
    val healthy: Color
        @Composable @ReadOnlyComposable get() = Color(0xFF4CAF50)

    /** 中继 / 中等延迟 / 轻微丢包。 */
    val warning: Color
        @Composable @ReadOnlyComposable get() = Color(0xFFFFA000)

    /** 错误 / 高丢包 / 加密关闭等危险项。 */
    val danger: Color
        @Composable @ReadOnlyComposable get() = MiuixTheme.colorScheme.error

    /** 中性 / 未知 / 占位。 */
    val neutral: Color
        @Composable @ReadOnlyComposable get() = MiuixTheme.colorScheme.onSurfaceVariantSummary

    /** 上行流量曲线标识色。 */
    val trafficUpload: Color
        @Composable @ReadOnlyComposable get() = Color(0xFF3482FF)

    /** 下行流量曲线标识色。 */
    val trafficDownload: Color
        @Composable @ReadOnlyComposable get() = Color(0xFF4CAF50)

    /**
     * 延迟分档：<10ms 绿、<50ms 橙、其余红。
     * 分档阈值集中在此，避免调用点各写一份魔法数。
     */
    @Composable
    @ReadOnlyComposable
    fun latency(latencyUs: Long): Color = when {
        latencyUs < 10_000 -> healthy
        latencyUs < 50_000 -> warning
        else -> danger
    }

    /** 丢包分档：<1% 绿、<5% 橙、其余红。 */
    @Composable
    @ReadOnlyComposable
    fun loss(rate: Float): Color = when {
        rate < 0.01f -> healthy
        rate < 0.05f -> warning
        else -> danger
    }
}
