<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { AxiosInstance } from 'axios'
import { Refresh, Download, WarningFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ api: AxiosInstance }>()
const date = (value: Date) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(value)
const today = new Date(), week = new Date(today.getTime() - 6 * 86400000)
const dates = ref<[string, string]>([date(week), date(today)])
const loading = ref(false), error = ref(''), overview = ref<any>(null), usage = ref<any>(null), lastUpdated = ref('')
const chartEl = ref<HTMLElement>(), view = ref('overview')
let chart: echarts.ECharts | undefined, observer: ResizeObserver | undefined
let requestVersion = 0
const number = (value: unknown) => new Intl.NumberFormat('zh-CN').format(Number(value || 0))
const money = (micros: unknown) => `¥${(Number(micros || 0) / 1000000).toFixed(6)}`
const count = (items: any[]) => (items || []).reduce((sum, item) => sum + Number(item.count), 0)
const summary = computed(() => usage.value?.summary || {})
const successRate = computed(() => {
  const terminal = Number(summary.value.succeeded || 0) + Number(summary.value.failed || 0) + Number(summary.value.interrupted || 0)
  return terminal ? `${(Number(summary.value.succeeded || 0) / terminal * 100).toFixed(1)}%` : '--'
})
const statusLabels: Record<string, string> = {
  PENDING_ASSIGNMENT: '待分配', PENDING_AGENT: '待客服', PENDING_CUSTOMER: '待用户', RESOLVED: '已解决',
  CLOSED: '已关闭', REJECTED: '已拒绝', CANCELLED: '已取消', PROCESSING: '处理中', PENDING: '待处理',
  SUCCEEDED: '成功', FAILED: '失败', INDEXED: '已索引', UPLOADED: '待索引', SUBMITTED: '已提交',
  WAITING_RETURN: '待寄回', RETURN_IN_TRANSIT: '退货在途', RETURN_RECEIVED: '已收货',
}
async function load() {
  if (!dates.value?.[0] || !dates.value?.[1]) return
  loading.value = true; error.value = ''
  const version = ++requestVersion
  const params = { from: dates.value[0], to: dates.value[1] }
  try {
    const [dashboard, ai] = await Promise.all([props.api.get('/admin/dashboard/overview', { params }), props.api.get('/admin/statistics/ai-usage', { params })])
    if (version !== requestVersion) return
    overview.value = dashboard.data.data; usage.value = ai.data.data
    lastUpdated.value = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date())
    await nextTick(); renderChart()
  } catch (e: any) {
    if (version !== requestVersion) return
    error.value = e.response?.data?.message || '统计数据加载失败'
    overview.value = null; usage.value = null
    observer?.disconnect(); chart?.dispose(); chart = undefined; observer = undefined
  }
  finally { if (version === requestVersion) loading.value = false }
}
function renderChart() {
  if (!chartEl.value) return
  chart ||= echarts.init(chartEl.value)
  chart.setOption({
    color: ['#228579', '#bf4b54', '#376eb6'],
    tooltip: { trigger: 'axis' }, legend: { top: 0, left: 0 },
    grid: { left: 46, right: 64, top: 44, bottom: 36, containLabel: true },
    xAxis: { type: 'category', data: usage.value.days.map((item: any) => item.day), axisLine: { lineStyle: { color: '#d6dce2' } } },
    yAxis: [{ type: 'value', minInterval: 1, name: '调用', splitLine: { lineStyle: { color: '#edf0f2' } } },
      { type: 'value', name: '已知成本 / 元', splitLine: { show: false } }],
    series: [
      { name: '成功', type: 'bar', stack: 'calls', data: usage.value.days.map((item: any) => item.succeeded), barMaxWidth: 28 },
      { name: '失败 / 中断', type: 'bar', stack: 'calls', data: usage.value.days.map((item: any) => Number(item.failed) + Number(item.interrupted)) },
      { name: '已知成本', type: 'line', yAxisIndex: 1, data: usage.value.days.map((item: any) => Number(item.knownCostMicros) / 1000000), symbolSize: 5 },
    ],
  })
  observer ||= new ResizeObserver(() => chart?.resize())
  observer.observe(chartEl.value)
}
function exportCsv() {
  if (!usage.value) return
  const lines = ['day,calls,succeeded,failed,interrupted,input_tokens,output_tokens,known_cost_micros,unknown_cost_calls']
  for (const item of usage.value.days) lines.push([item.day, item.calls, item.succeeded, item.failed, item.interrupted, item.inputTokens, item.outputTokens, item.knownCostMicros, item.unknownCostCalls].join(','))
  const url = URL.createObjectURL(new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = `ai-usage-${usage.value.from}-${usage.value.to}.csv`; link.click(); URL.revokeObjectURL(url)
}
onMounted(load)
watch(view, async value => { if (value === 'overview') { await nextTick(); chart?.resize() } })
onBeforeUnmount(() => { ++requestVersion; observer?.disconnect(); chart?.dispose() })
</script>

<template>
  <section class="dashboard" v-loading="loading">
    <div class="dashboard-heading">
      <div><h2>运营看板</h2><p>{{ dates?.[0] }} 至 {{ dates?.[1] }} · 上海时间</p></div>
      <div class="toolbar">
        <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :clearable="false" range-separator="至" @change="load" />
        <el-tooltip content="刷新数据"><el-button :icon="Refresh" circle :disabled="loading" aria-label="刷新数据" @click="load" /></el-tooltip>
        <el-tooltip content="导出每日用量"><el-button :icon="Download" circle :disabled="!usage || loading" aria-label="导出每日用量" @click="exportCsv" /></el-tooltip>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-empty v-if="!overview && !loading" description="暂无统计数据"><el-button @click="load">重新加载</el-button></el-empty>
    <template v-if="overview && usage">
      <div class="stats-band">
        <div><span>工单</span><strong>{{ number(count(overview.tickets)) }}</strong><small>所选日期创建</small></div>
        <div><span>AI 调用</span><strong>{{ number(summary.calls) }}</strong><small>历史 {{ number(summary.historical) }} · 待处理 {{ number(summary.pending) }}</small></div>
        <div><span>AI 成功率</span><strong>{{ successRate }}</strong><small>失败 {{ number(summary.failed) }} · 中断 {{ number(summary.interrupted) }}</small></div>
        <div><span>已知 AI 成本</span><strong class="cost">{{ money(summary.knownCostMicros) }}</strong><small>未知成本 {{ number(summary.unknownCostCalls) }} 次</small></div>
      </div>
      <el-tabs v-model="view">
        <el-tab-pane label="运营概览" name="overview" />
        <el-tab-pane label="模型用量" name="usage" />
      </el-tabs>
      <div v-show="view === 'overview'">
        <div class="chart-heading"><h3>AI 调用与成本</h3><small v-if="lastUpdated">更新于 {{ lastUpdated }}</small></div>
        <div ref="chartEl" class="usage-chart" role="img" aria-label="每日 AI 调用与已知成本趋势" />
        <div class="operations">
          <section><h3>工单状态</h3><el-empty v-if="!overview.tickets.length" :image-size="60" description="暂无工单" /><div v-for="item in overview.tickets" :key="item.status" class="status-row"><span>{{ statusLabels[item.status] || item.status }}</span><strong>{{ number(item.count) }}</strong></div></section>
          <section><h3>异步与知识库</h3><div class="status-row"><span>Outbox 未发布</span><strong :class="{ warning: overview.outbox.pending > 100 }">{{ number(overview.outbox.pending) }}</strong></div><div class="status-row"><span>最老事件等待</span><strong :class="{ warning: overview.outbox.oldestAgeSeconds > 300 }">{{ number(overview.outbox.oldestAgeSeconds) }} 秒</strong></div><div class="status-row"><span>发布失败</span><strong>{{ number(overview.outbox.failed) }}</strong></div><div v-for="item in overview.tasks" :key="item.status" class="status-row"><span>AI 任务 · {{ statusLabels[item.status] || item.status }}</span><strong>{{ number(item.count) }}</strong></div><div v-for="item in overview.documents" :key="item.status" class="status-row"><span>文档 · {{ statusLabels[item.status] || item.status }}</span><strong>{{ number(item.count) }}</strong></div></section>
          <section><h3>AI 预算</h3><div class="status-row"><span>今日已知成本</span><strong>{{ money(overview.budget.today.knownCostMicros) }}</strong></div><div class="status-row"><span>项目已知成本</span><strong>{{ money(overview.budget.project.knownCostMicros) }}</strong></div><el-progress :percentage="Math.min(100, Number(overview.budget.project.knownCostMicros) / overview.budget.projectHardLimitMicros * 100)" :status="overview.budget.projectLimitReached ? 'exception' : undefined" /><p class="budget-note">阈值 {{ money(overview.budget.projectHardLimitMicros) }} · 未知 {{ number(overview.budget.project.unknownCostCalls) }} 次</p><el-tag v-if="overview.budget.projectLimitReached" type="danger">已知费用达到项目限额</el-tag><el-tag v-else-if="overview.budget.projectWarning || overview.budget.dailyWarning" type="warning">已知费用达到预警阈值</el-tag><div v-if="overview.budget.project.unknownCostCalls" class="unknown-note"><el-icon><WarningFilled /></el-icon><span>存在未知成本，实际费用待核实</span></div><p class="budget-note">预算仅监测，未自动阻断调用</p><div class="status-row"><span>模拟退款</span><strong>¥{{ (Number(overview.refunds.amountCent) / 100).toFixed(2) }}</strong></div></section>
        </div>
      </div>
      <div v-if="view === 'usage'" class="usage-detail">
        <div class="usage-totals"><span>已知输入 Token <strong>{{ number(summary.inputTokens) }}</strong></span><span>已知输出 Token <strong>{{ number(summary.outputTokens) }}</strong></span><span>未知 Token <strong>{{ number(summary.unknownTokenCalls) }} 次</strong></span><span>平均延迟 <strong>{{ summary.avgLatencyMs == null ? '--' : `${Number(summary.avgLatencyMs).toFixed(0)} ms` }}</strong></span></div>
        <el-table :data="usage.models" stripe empty-text="暂无模型调用" style="width:100%">
          <el-table-column prop="provider" label="供应商" min-width="130" /><el-table-column prop="model" label="模型" min-width="150" />
          <el-table-column prop="calls" label="调用" min-width="90" /><el-table-column prop="failed" label="失败" min-width="80" />
          <el-table-column label="已知成本" min-width="140"><template #default="{ row }">{{ money(row.knownCostMicros) }}</template></el-table-column>
          <el-table-column prop="unknownCostCalls" label="未知成本调用" min-width="120" />
        </el-table>
        <el-table :data="usage.days" stripe class="daily-table" empty-text="暂无调用">
          <el-table-column prop="day" label="日期" min-width="120" /><el-table-column prop="calls" label="调用" min-width="80" /><el-table-column prop="succeeded" label="成功" min-width="80" /><el-table-column prop="failed" label="失败" min-width="80" /><el-table-column prop="interrupted" label="中断" min-width="80" /><el-table-column label="已知成本" min-width="140"><template #default="{ row }">{{ money(row.knownCostMicros) }}</template></el-table-column><el-table-column prop="unknownCostCalls" label="未知成本调用" min-width="120" />
        </el-table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.dashboard{width:100%;color:#26313b}.dashboard-heading{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:20px 0 24px}.dashboard-heading h2{font-size:24px;margin:0 0 8px}.dashboard-heading p{margin:0;font-size:13px;color:#747d86}.toolbar{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.toolbar :deep(.el-date-editor){max-width:330px;width:330px}.stats-band{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));border-top:1px solid #dfe4e8;border-bottom:1px solid #dfe4e8;padding:24px 0;margin:0 0 20px}.stats-band>div{padding:0 22px;border-right:1px solid #e3e7ea;min-width:0}.stats-band>div:first-child{padding-left:0}.stats-band>div:last-child{border:0}.stats-band span,.stats-band small{display:block;color:#737e88;font-size:13px}.stats-band strong{display:block;font-size:30px;margin:9px 0;color:#26313b}.stats-band .cost{font-size:25px;color:#228579;overflow-wrap:anywhere}.chart-heading{display:flex;align-items:center;justify-content:space-between;padding-top:8px}.chart-heading h3,.operations h3{font-size:16px;margin:0 0 18px}.chart-heading small{font-size:12px;color:#848d96}.usage-chart{height:310px;width:100%;margin:8px 0 26px}.operations{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));border-top:1px solid #e0e5e8;padding:24px 0;gap:32px}.operations section{min-width:0}.status-row{display:flex;justify-content:space-between;gap:16px;align-items:baseline;padding:10px 0;border-bottom:1px solid #f0f2f4;font-size:13px}.status-row span{color:#65717b}.status-row strong{font-weight:600;overflow-wrap:anywhere;text-align:right}.warning{color:#bd5842}.budget-note{color:#7d8790;font-size:12px;line-height:1.6;margin:9px 0}.unknown-note{display:flex;gap:6px;align-items:center;color:#a86331;font-size:12px;margin-top:12px}.operations :deep(.el-progress){margin-top:15px}.usage-totals{display:flex;gap:26px;flex-wrap:wrap;padding:18px 0 24px;color:#737e88;font-size:13px}.usage-totals strong{color:#26313b;padding-left:5px}.daily-table{margin-top:26px}.usage-detail{padding-bottom:24px}@media(max-width:860px){.dashboard-heading{align-items:flex-start;flex-direction:column}.stats-band{grid-template-columns:repeat(2,minmax(0,1fr));gap:24px 0}.stats-band>div:nth-child(3){padding-left:0}.stats-band>div:nth-child(2){border:0}.operations{grid-template-columns:1fr;gap:24px}.toolbar :deep(.el-date-editor){width:280px;max-width:100%}.toolbar{width:100%}.stats-band strong{font-size:26px}.stats-band .cost{font-size:21px}.usage-chart{height:280px}}
</style>
