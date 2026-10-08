import { afterEach, describe, expect, it, vi } from 'vitest'
import fixture from '../../contracts/chat-sse.json'
import { consumeSse } from './sse'
import { useSseChat } from './useSseChat'

function stream(text: string, width = 1) {
  const bytes = new TextEncoder().encode(text)
  return new ReadableStream<Uint8Array>({ start(controller) {
    for (let i = 0; i < bytes.length; i += width) controller.enqueue(bytes.slice(i, i + width))
    controller.close()
  } })
}
const frames = fixture.map(e => `event: ${e.event}\ndata: ${JSON.stringify(e.data)}\n\n`).join('')
afterEach(() => vi.unstubAllGlobals())

describe('shared SSE contract', () => {
  it.each([1, 7, 1024])('decodes Unicode and split lines at %i bytes', async width => {
    const received: unknown[] = []
    await consumeSse(stream(': ping\n\n' + frames, width), e => received.push(e))
    expect(received).toEqual(fixture)
  })
  it('supports CRLF, multiline JSON data and terminal cancellation', async () => {
    const received: unknown[] = []
    await consumeSse(stream('event: token\r\ndata: {"text":\r\ndata: "你好"}\r\n\r\nevent: done\r\ndata: {}\r\n\r\nevent: token\r\ndata: {"text":"ignored"}\r\n\r\n'), e => received.push(e))
    expect(received).toEqual([{ event: 'token', data: { text: '你好' } }, { event: 'done', data: {} }])
  })
  it('rejects truncated and invalid JSON responses', async () => {
    await expect(consumeSse(stream('event: token\ndata: {}\n\n'), () => {})).rejects.toThrow('AI_CHAT_INCOMPLETE')
    await expect(consumeSse(stream('event: token\ndata: broken\n\n'), () => {})).rejects.toThrow()
  })
  it('updates composable state and sends authentication', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(stream(frames)))
    vi.stubGlobal('fetch', fetchMock)
    const chat = useSseChat('/api/v1', 'test-token')
    await chat.send(60001, '政策是什么')
    expect(chat.text.value).toBe(fixture[1].data.text)
    expect(chat.error.value).toBe('')
    expect(chat.loading.value).toBe(false)
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token')
  })
  it.each([['event: error\ndata: {"code":"AI_PROVIDER_FAILED"}\n\n', 'AI_PROVIDER_FAILED'], ['', 'AI_CHAT_INCOMPLETE']])('exposes stream failure', async (wire, code) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(stream(wire))))
    const chat = useSseChat('/api/v1', 'token')
    await chat.send(1, 'hello')
    expect(chat.error.value).toBe(code)
    expect(chat.loading.value).toBe(false)
  })
  it('exposes non-success HTTP responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })))
    const chat = useSseChat('/api/v1', 'token')
    await chat.send(1, 'hello')
    expect(chat.error.value).toBe('AI_CHAT_UNAVAILABLE')
  })
})
