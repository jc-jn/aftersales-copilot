export interface SseEvent { event: string; data: Record<string, any> }

// Keep decoder and partial lines alive across arbitrary network byte boundaries.
export async function consumeSse(body: ReadableStream<Uint8Array>, onEvent: (event: SseEvent) => void) {
  const reader = body.getReader(), decoder = new TextDecoder()
  let buffer = '', eventName = 'message', dataLines: string[] = [], terminal = false
  function dispatch() {
    if (!dataLines.length) { eventName = 'message'; return }
    const event = { event: eventName, data: JSON.parse(dataLines.join('\n')) }
    dataLines = []; eventName = 'message'
    onEvent(event)
    terminal = event.event === 'done' || event.event === 'error'
  }
  function process(final = false) {
    while (!terminal) {
      const newline = buffer.indexOf('\n')
      if (newline < 0) break
      const line = buffer.slice(0, newline).replace(/\r$/, '')
      buffer = buffer.slice(newline + 1)
      if (!line) { dispatch(); continue }
      if (line.startsWith(':')) continue
      const colon = line.indexOf(':')
      const field = colon < 0 ? line : line.slice(0, colon)
      const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '')
      if (field === 'event') eventName = value
      if (field === 'data') dataLines.push(value)
    }
    if (final && !terminal) throw new Error('AI_CHAT_INCOMPLETE')
  }
  try {
    while (!terminal) {
      const { value, done } = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      process(done)
    }
  } finally {
    try { await reader.cancel() } finally { reader.releaseLock() }
  }
}
