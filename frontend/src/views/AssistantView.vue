<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { Promotion } from '@element-plus/icons-vue'
import { api, ApiError } from '@/api/http'
import type { ChatReply } from '@/api/types'
import { useAuth } from '@/stores/auth'

interface Msg {
  role: 'user' | 'assistant'
  content: string
  tools?: string[]
  notice?: string
  error?: boolean
}

const auth = useAuth()
const mode = ref<'llm' | 'offline' | null>(null)
const messages = ref<Msg[]>([])
const input = ref('')
const thinking = ref(false)
const scroller = ref<HTMLElement>()

const suggestions = [
  'How is the business doing this week?',
  'What are my top 5 products in the last 30 days?',
  'Which products need restocking?',
  'Show the order status breakdown for the last 7 days',
  'What was our best day this month?',
]

onMounted(async () => {
  try {
    mode.value = (await api.get<{ mode: 'llm' | 'offline' }>('/ai/status')).mode
  } catch {
    mode.value = null
  }
})

const render = (md: string) => DOMPurify.sanitize(marked.parse(md, { async: false }) as string)

async function scrollDown() {
  await nextTick()
  scroller.value?.scrollTo({ top: scroller.value.scrollHeight, behavior: 'smooth' })
}

async function send(text?: string) {
  const message = (text ?? input.value).trim()
  if (!message || thinking.value) return
  const history = messages.value.filter((m) => !m.error).slice(-10).map((m) => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: message })
  input.value = ''
  thinking.value = true
  scrollDown()
  try {
    const r = await api.post<ChatReply>('/ai/chat', { message, history })
    mode.value = r.mode
    messages.value.push({ role: 'assistant', content: r.reply, tools: r.toolsUsed, notice: r.notice })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: e instanceof ApiError ? e.message : String(e), error: true })
  } finally {
    thinking.value = false
    scrollDown()
  }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <div class="page assistant">
    <div class="page-header">
      <div>
        <h1>AI Copilot</h1>
        <p>Ask about {{ auth.me?.tenant.name }}'s live data. Read-only, tenant-scoped tools — every number comes from the database.</p>
      </div>
      <el-tooltip :content="mode === 'llm' ? 'Spring AI ChatClient with function calling' : 'No LLM key configured: the built-in analyst routes your question to the same tools'">
        <el-tag :type="mode === 'llm' ? 'success' : 'info'" effect="light" round data-testid="ai-mode">
          {{ mode === 'llm' ? 'LLM mode' : 'Offline analyst' }}
        </el-tag>
      </el-tooltip>
    </div>

    <div class="card chat">
      <div ref="scroller" class="messages">
        <div v-if="!messages.length" class="empty">
          <div class="spark">✦</div>
          <h3>What would you like to know?</h3>
          <div class="sugs">
            <button v-for="s in suggestions" :key="s" class="sug" @click="send(s)">{{ s }}</button>
          </div>
        </div>
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble" :class="{ error: m.error }">
            <div v-if="m.role === 'assistant'" class="markdown" v-html="render(m.content)" />
            <div v-else>{{ m.content }}</div>
            <div v-if="m.tools?.length" class="tools">
              <span class="muted">Data from</span>
              <el-tag v-for="t in m.tools" :key="t" size="small" effect="plain" class="mono">{{ t }}()</el-tag>
            </div>
            <div v-if="m.notice" class="notice">{{ m.notice }}</div>
          </div>
        </div>
        <div v-if="thinking" class="msg assistant">
          <div class="bubble typing"><i /><i /><i /></div>
        </div>
      </div>
      <div class="composer">
        <el-input v-model="input" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }" resize="none"
                  placeholder="Ask about revenue, best sellers, stock, a specific order…" data-testid="ai-input"
                  @keydown="onKey" />
        <el-button type="primary" :icon="Promotion" :loading="thinking" :disabled="!input.trim()" data-testid="ai-send"
                   @click="send()">Send</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.assistant {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 480px;
  overflow: hidden;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.empty {
  text-align: center;
  padding: 40px 0;
}

.spark {
  font-size: 30px;
  background: linear-gradient(135deg, #a855f7, #6366f1);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.empty h3 {
  margin: 8px 0 20px;
}

.sugs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 720px;
  margin: 0 auto;
}

.sug {
  font: inherit;
  font-size: 13px;
  border: 1px solid var(--op-border);
  background: #fff;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
}

.sug:hover {
  border-color: var(--op-primary);
  color: var(--op-primary);
}

.msg {
  display: flex;
  margin-bottom: 14px;
}

.msg.user {
  justify-content: flex-end;
}

.bubble {
  max-width: min(760px, 85%);
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.55;
}

.user .bubble {
  background: var(--op-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
  white-space: pre-wrap;
}

.assistant .bubble {
  background: #f8fafc;
  border: 1px solid var(--op-border);
  border-bottom-left-radius: 4px;
}

.bubble.error {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.tools {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 10px;
  font-size: 12px;
}

.notice {
  margin-top: 8px;
  font-size: 12px;
  color: #b45309;
}

.typing {
  display: flex;
  gap: 4px;
}

.typing i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
  animation: blink 1.2s infinite;
}

.typing i:nth-child(2) {
  animation-delay: 0.2s;
}

.typing i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 14px 16px;
  border-top: 1px solid var(--op-border);
  background: #fcfcfd;
}
</style>
