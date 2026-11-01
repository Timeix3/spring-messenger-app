export type User = {
  id: number
  username: string
  displayName: string | null
  createdAt: string
}

export type Message = {
  id: number
  conversationId: number
  sender: User
  content: string
  createdAt: string
}

export type Conversation = {
  id: number
  user1: User
  user2: User
  lastMessage: Message | null
  lastMessageAt: string | null
  createdAt: string
}

export type ConversationListItem = {
  id: number
  otherUser: User
  lastMessage: Message | null
  lastMessageAt: string | null
  unreadCount: number
}

export type Presence = {
  userId: number
  username: string
  displayName: string | null
  status: 'ONLINE' | 'OFFLINE'
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type AuthResponse = {
  tokenType: string
  accessToken: string
  expiresInSeconds: number
  userId: number
  username: string
  displayName: string | null
}

export type PresenceStatus = Presence['status']
