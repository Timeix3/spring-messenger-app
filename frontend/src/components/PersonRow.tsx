import { avatarLetters, displayName, formatTime } from '../lib/format'
import type { ConversationListItem, PresenceStatus, User } from '../types'

export function PersonRow({
  conversation,
  user,
  isSelected,
  onClick,
  presence,
}: {
  conversation?: ConversationListItem
  user: User
  isSelected: boolean
  onClick: () => void
  presence: PresenceStatus
}) {
  return (
    <button className={`person-row ${isSelected ? 'selected' : ''}`} onClick={onClick} type="button">
      <span className="avatar-wrap">
        <span className="avatar">{avatarLetters(user)}</span>
        <i className={`avatar-presence ${presence === 'ONLINE' ? 'online' : ''}`} />
      </span>
      <span className="person-info">
        <span className="person-topline">
          <strong>{displayName(user)}</strong>
          <time>{formatTime(conversation?.lastMessageAt ?? null)}</time>
        </span>
        <span className="person-bottomline">
          <span className="message-preview">
            {conversation?.lastMessage?.content ?? `@${user.username}`}
          </span>
          {Boolean(conversation?.unreadCount) && <span className="unread-badge">{conversation?.unreadCount}</span>}
        </span>
      </span>
    </button>
  )
}
