import { useCallback, useMemo, useState } from 'react'
import { AuthPage } from './components/AuthPage'
import { ConversationPanel } from './components/ConversationPanel'
import { Sidebar, type SidebarTab } from './components/Sidebar'
import { useAuth } from './hooks/useAuth'
import { useChatMessages } from './hooks/useChatMessages'
import { useChatSocket } from './hooks/useChatSocket'
import { useMessengerData } from './hooks/useMessengerData'
import { getErrorMessage } from './lib/api'
import type { User } from './types'

function App() {
  const [error, setError] = useState('')
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null)
  const [activeTab, setActiveTab] = useState<SidebarTab>('contacts')
  const reportError = useCallback((message: string) => setError(message), [])

  const auth = useAuth(reportError)
  const messenger = useMessengerData(
    auth.token,
    selectedUserId,
    auth.expireSession,
    auth.setLoading,
    reportError,
  )
  const conversationByUserId = useMemo(
    () => new Map(messenger.conversations.map((conversation) => [conversation.otherUser.id, conversation])),
    [messenger.conversations],
  )
  const selectedUser = messenger.users.find((user) => user.id === selectedUserId) ?? null
  const activeConversation = selectedUser ? conversationByUserId.get(selectedUser.id) : undefined
  const chat = useChatMessages(
    activeConversation?.id ?? null,
    messenger.markRead,
    messenger.handleSentMessage,
    reportError,
  )
  const socketConnected = useChatSocket(
    auth.token,
    messenger.conversations,
    (message) => {
      messenger.handleIncomingMessage(message)
      chat.receiveMessage(message)
    },
    reportError,
  )

  async function openUser(user: User) {
    setError('')
    setSelectedUserId(user.id)
    try {
      await messenger.openConversation(user)
    } catch (requestError) {
      setSelectedUserId(null)
      reportError(getErrorMessage(requestError))
    }
  }

  function handleBackToList() {
    setSelectedUserId(null)
  }

  async function handleAuthenticate(
    mode: 'login' | 'register',
    username: string,
    password: string,
    displayName?: string,
  ) {
    await auth.authenticate(mode, username, password, displayName)
  }

  async function handleLogout() {
    await auth.logout()
    setSelectedUserId(null)
  }

  if (!auth.token) return <AuthPage onAuthenticate={handleAuthenticate} />

  if (auth.loading) {
    return <main className="loading-screen"><div className="loader" />Загружаем мессенджер…</main>
  }

  if (!messenger.currentUser) {
    return (
      <main className="loading-screen">
        <p className="fatal-error">{error || 'Не удалось загрузить профиль.'}</p>
        <button className="primary-button" onClick={() => void handleLogout()} type="button">Войти снова</button>
      </main>
    )
  }

  return (
    <main className={`messenger-shell ${selectedUserId ? 'conversation-open' : 'list-open'}`}>
      <Sidebar
        currentUser={messenger.currentUser}
        users={messenger.users}
        conversations={messenger.conversations}
        presence={messenger.presence}
        selectedUserId={selectedUserId}
        socketConnected={socketConnected}
        activeTab={activeTab}
        onActiveTabChange={setActiveTab}
        onOpenUser={(user) => void openUser(user)}
        onLogout={() => void handleLogout()}
      />
      <ConversationPanel
        currentUser={messenger.currentUser}
        user={selectedUser && activeConversation ? selectedUser : null}
        presence={selectedUser ? messenger.presence[selectedUser.id] ?? 'OFFLINE' : 'OFFLINE'}
        messages={chat.messages}
        loading={chat.loading}
        loadingOlder={chat.loadingOlder}
        hasOlderMessages={chat.hasOlderMessages}
        sending={chat.sending}
        error={error}
        onClearError={() => setError('')}
        onBack={handleBackToList}
        onLoadOlder={() => void chat.loadOlderMessages()}
        onSend={chat.sendMessage}
        onShowContacts={() => setActiveTab('contacts')}
      />
    </main>
  )
}

export default App
