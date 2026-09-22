import { ref } from 'vue'

export function useSseChat(baseUrl: string, token: string) {
  const text = ref(''), citations = ref<any[]>([]), loading = ref(false), error = ref('')
  async function send(ticketId: number, message: string) {
    text.value = ''; citations.value = []; error.value = ''; loading.value = true
    try {
      const response = await fetch(`${baseUrl}/tickets/${ticketId}/ai-chat/stream`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ message }) })
      if (!response.ok || !response.body) throw new Error('AI_CHAT_UNAVAILABLE')
      const reader = response.body.getReader(), decoder = new TextDecoder(); let buffer = ''
      while (true) {
        const { value, done } = await reader.read(); if (done) break
        buffer += decoder.decode(value, { stream: true }); const frames = buffer.split('\n\n'); buffer = frames.pop() || ''
        for (const frame of frames) { const event = frame.match(/^event:\s*(.+)$/m)?.[1]; const raw = frame.match(/^data:\s*(.+)$/m)?.[1]; if (!raw) continue; const data = JSON.parse(raw); if (event === 'token') text.value += data.text || ''; if (event === 'meta') citations.value = data.citations || []; if (event === 'error') throw new Error(data.code || 'AI_CHAT_FAILED') }
      }
    } catch (e: any) { error.value = e.message || 'AI 对话暂不可用' } finally { loading.value = false }
  }
  return { text, citations, loading, error, send }
}
