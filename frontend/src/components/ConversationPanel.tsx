import { useEffect, useRef, useState, type FormEvent } from 'react'
import { avatarLetters, displayName, formatMessageTime } from '../lib/format'
import type { Message, PresenceStatus, User } from '../types'

export function ConversationPanel({
  currentUser,
  user,
  presence,
  messages,
  loading,
  loadingOlder,
  hasOlderMessages,
  sending,
  error,
  onClearError,
  onBack,
  onLoadOlder,
  onSend,
  onShowContacts,
}: {
  currentUser: User
  user: User | null
  presence: PresenceStatus
  messages: Message[]
  loading: boolean
  loadingOlder: boolean
  hasOlderMessages: boolean
  sending: boolean
  error: string
  onClearError: () => void
  onBack: () => void
  onLoadOlder: () => void
  onSend: (content: string) => Promise<boolean>
  onShowContacts: () => void
}) {
  const [draft, setDraft] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const lastMessageId = messages.at(-1)?.id

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [lastMessageId, user?.id])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const sent = await onSend(draft)
    if (sent) setDraft('')
  }

  return (
    <section className="conversation-panel">
      {error && (
        <div className="toast-error" role="alert">
          <span>{error}</span>
          <button onClick={onClearError} type="button" aria-label="Закрыть сообщение">×</button>
        </div>
      )}

      {user ? (
        <>
          <header className="conversation-header">
            <button className="mobile-back" onClick={onBack} type="button" aria-label="Вернуться к списку">←</button>
            <div className="avatar conversation-avatar">{avatarLetters(user)}</div>
            <div className="conversation-title">
              <h1>{displayName(user)}</h1>
              <span>
                <i className={`presence-indicator ${presence === 'ONLINE' ? 'online' : ''}`} />
                {presence === 'ONLINE' ? 'В сети' : `@${user.username}`}
              </span>
            </div>
          </header>

          <div className="messages-area">
            {hasOlderMessages && (
              <button className="older-button" disabled={loadingOlder} onClick={onLoadOlder} type="button">
                {loadingOlder ? 'Загружаем…' : 'Загрузить предыдущие сообщения'}
              </button>
            )}
            {loading && messages.length === 0 && <div className="messages-loading">Загружаем сообщения…</div>}
            {!loading && messages.length === 0 && (
              <div className="empty-conversation">
                <div className="empty-icon" aria-hidden="true">✦</div>
                <h2>Начните разговор</h2>
                <p>Напишите первое сообщение — оно появится здесь.</p>
              </div>
            )}
            {messages.map((message, index) => {
              const isOwn = message.sender.id === currentUser.id
              const previous = messages[index - 1]
              const showDate = !previous
                || new Date(previous.createdAt).toDateString() !== new Date(message.createdAt).toDateString()
              return (
                <div className="message-block" key={message.id}>
                  {showDate && (
                    <div className="date-separator">
                      <span>{new Date(message.createdAt).toLocaleDateString('ru-RU', {
                        day: 'numeric',
                        month: 'long',
                        year: new Date(message.createdAt).getFullYear() === new Date().getFullYear() ? undefined : 'numeric',
                      })}</span>
                    </div>
                  )}
                  <div className={`message-row ${isOwn ? 'own' : ''}`}>
                    <div className="message-bubble">
                      <p>{message.content}</p>
                      <time dateTime={message.createdAt}>{formatMessageTime(message.createdAt)}</time>
                    </div>
                  </div>
                </div>
              )
            })}
            <div ref={messagesEndRef} />
          </div>

          <form className="composer" onSubmit={(event) => void submit(event)}>
            <textarea
              aria-label="Текст сообщения"
              maxLength={4000}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  event.currentTarget.form?.requestSubmit()
                }
              }}
              placeholder="Напишите сообщение…"
              rows={1}
              value={draft}
            />
            <div className="composer-bottom">
              <span>Enter — отправить · Shift + Enter — новая строка</span>
              <div className="composer-actions">
                {draft.length > 3500 && <span className="character-count">{draft.length}/4000</span>}
                <button className="send-button" disabled={!draft.trim() || sending} type="submit">
                  <span>{sending ? 'Отправка…' : 'Отправить'}</span>
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m21 3-7.2 18-3.8-7-7-3.8L21 3Z" /><path d="M10 14 21 3" /></svg>
                </button>
              </div>
            </div>
          </form>
        </>
      ) : (
        <div className="welcome-panel">
          <div className="welcome-art" aria-hidden="true">
            <div className="art-bubble bubble-one">Привет!</div>
            <div className="art-bubble bubble-two">Как дела?</div>
            <div className="art-sparkle">✳</div>
          </div>
          <span className="eyebrow">ПРОСТО. ЛИЧНО. ВОВРЕМЯ.</span>
          <h1>Хорошие разговоры<br />начинаются здесь.</h1>
          <p>Выберите собеседника слева или найдите кого-нибудь в списке пользователей.</p>
          <button className="welcome-action" onClick={onShowContacts} type="button">
            Посмотреть пользователей <span>→</span>
          </button>
        </div>
      )}
    </section>
  )
}
