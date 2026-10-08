import { ref } from 'vue'
import { consumeSse } from './sse'

export function useSseChat(baseUrl: string, token: string) {
  const text = ref(''), citations = ref<any[]>([]), loading = ref(false), error = ref('')
  async function send(ticketId: number, message: string) {
    text.value = ''; citations.value = []; error.value = ''; loading.value = true
    try {
      const response = await fetch(`${baseUrl}/tickets/${ticketId}/ai-chat/stream`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ message }) })
      if (!response.ok || !response.body) throw new Error('AI_CHAT_UNAVAILABLE')
      await consumeSse(response.body, ({ event, data }) => {
        if (event === 'token') text.value += data.text || ''
        if (event === 'meta' || event === 'done') citations.value = data.citations || []
        if (event === 'error') throw new Error(data.code || 'AI_CHAT_FAILED')
      })
    } catch (e: any) { error.value = e.message || 'AI 对话暂不可用' } finally { loading.value = false }
  }
  return { text, citations, loading, error, send }
}
