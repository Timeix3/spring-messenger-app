import { useState } from 'react'
import { avatarLetters, displayName } from '../lib/format'
import type { ConversationListItem, PresenceStatus, User } from '../types'
import { PersonRow } from './PersonRow'

export type SidebarTab = 'chats' | 'contacts'

export function Sidebar({
  currentUser,
  users,
  conversations,
  presence,
  selectedUserId,
  socketConnected,
  activeTab,
  onActiveTabChange,
  onOpenUser,
  onLogout,
}: {
  currentUser: User
  users: User[]
  conversations: ConversationListItem[]
  presence: Record<number, PresenceStatus>
  selectedUserId: number | null
  socketConnected: boolean
  activeTab: SidebarTab
  onActiveTabChange: (tab: SidebarTab) => void
  onOpenUser: (user: User) => void
  onLogout: () => void
}) {
  const [search, setSearch] = useState('')
  const conversationByUserId = new Map(
    conversations.map((conversation) => [conversation.otherUser.id, conversation]),
  )
  const totalUnread = conversations.reduce((total, conversation) => total + conversation.unreadCount, 0)
  const query = search.trim().toLocaleLowerCase('ru')
  const filteredUsers = users.filter((user) => (
    displayName(user).toLocaleLowerCase('ru').includes(query)
    || user.username.toLocaleLowerCase('ru').includes(query)
  ))
  const sortedConversations = conversations
    .slice()
    .sort((a, b) => (b.lastMessageAt ?? '').localeCompare(a.lastMessageAt ?? ''))

  return (
    <aside className="sidebar">
      <header className="sidebar-header">
        <div className="brand-lockup">
          <div className="brand-mark small" aria-hidden="true">м</div>
          <div>
            <strong>мессенджер</strong>
            <span>простое общение</span>
          </div>
        </div>
        <button className="icon-button logout-button" onClick={onLogout} title="Выйти" type="button" aria-label="Выйти">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 17l5-5-5-5M15 12H3m9-7h6a2 2 0 012 2v10a2 2 0 01-2 2h-6" /></svg>
        </button>
      </header>

      <div className="profile-strip">
        <div className="avatar self-avatar">{avatarLetters(currentUser)}</div>
        <div className="profile-copy">
          <strong>{displayName(currentUser)}</strong>
          <span>@{currentUser.username}</span>
        </div>
        <span className={`connection-dot ${socketConnected ? 'online' : ''}`} title={socketConnected ? 'Подключено' : 'Подключение…'} />
      </div>

      <div className="sidebar-heading">
        <div>
          <span className="eyebrow">ОБЩЕНИЕ</span>
          <h2>Сообщения</h2>
        </div>
        {totalUnread > 0 && <span className="unread-total">{totalUnread}</span>}
      </div>

      <label className="search-box">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.5 4.5" /></svg>
        <input
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Найти собеседника"
          value={search}
        />
        {search && <button onClick={() => setSearch('')} type="button" aria-label="Очистить поиск">×</button>}
      </label>

      <div className="sidebar-tabs" role="tablist" aria-label="Список пользователей">
        <button
          className={activeTab === 'chats' ? 'selected' : ''}
          onClick={() => onActiveTabChange('chats')}
          role="tab"
          aria-selected={activeTab === 'chats'}
          type="button"
        >
          Диалоги
          <span>{conversations.length}</span>
        </button>
        <button
          className={activeTab === 'contacts' ? 'selected' : ''}
          onClick={() => onActiveTabChange('contacts')}
          role="tab"
          aria-selected={activeTab === 'contacts'}
          type="button"
        >
          Все пользователи
          <span>{users.length}</span>
        </button>
      </div>

      <div className="people-list">
        {activeTab === 'chats' && !search ? (
          sortedConversations.length ? sortedConversations.map((conversation) => (
            <PersonRow
              key={conversation.id}
              conversation={conversation}
              user={conversation.otherUser}
              isSelected={selectedUserId === conversation.otherUser.id}
              onClick={() => onOpenUser(conversation.otherUser)}
              presence={presence[conversation.otherUser.id] ?? 'OFFLINE'}
            />
          )) : <div className="list-empty">Здесь появятся ваши диалоги. Выберите пользователя, чтобы начать общение.</div>
        ) : filteredUsers.length ? filteredUsers.map((user) => (
          <PersonRow
            key={user.id}
            conversation={conversationByUserId.get(user.id)}
            user={user}
            isSelected={selectedUserId === user.id}
            onClick={() => onOpenUser(user)}
            presence={presence[user.id] ?? 'OFFLINE'}
          />
        )) : <div className="list-empty">Пользователи не найдены.</div>}
      </div>

      <footer className="sidebar-footer">
        <span className={`presence-indicator ${socketConnected ? 'online' : ''}`} />
        {socketConnected ? 'На связи' : 'Подключение к серверу'}
        <span className="footer-version">SPRING CHAT</span>
      </footer>
    </aside>
  )
}
