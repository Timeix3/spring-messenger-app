import axios from 'axios'
import { useCallback, useEffect, useRef, useState } from 'react'
import { api, getAllPages, getErrorMessage } from '../lib/api'
import type {
  Conversation,
  ConversationListItem,
  Message,
  Presence,
  PresenceStatus,
  User,
} from '../types'

export function useMessengerData(
  token: string | null,
  selectedUserId: number | null,
  onUnauthorized: () => void,
  setLoading: (loading: boolean) => void,
  onError: (message: string) => void,
) {
  const [currentUser, setCurrentUser] = useState<User | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [conversations, setConversations] = useState<ConversationListItem[]>([])
  const [presence, setPresence] = useState<Record<number, PresenceStatus>>({})
  const currentUserRef = useRef(currentUser)
  const selectedConversationId = conversations.find(
    (conversation) => conversation.otherUser.id === selectedUserId,
  )?.id ?? null
  const selectedConversationIdRef = useRef(selectedConversationId)

  useEffect(() => {
    currentUserRef.current = currentUser
  }, [currentUser])

  useEffect(() => {
    selectedConversationIdRef.current = selectedConversationId
  }, [selectedConversationId])

  useEffect(() => {
    if (!token) return

    let cancelled = false

    async function loadMessenger() {
      try {
        const [me, directory, chats, statuses] = await Promise.all([
          api.get<User>('/api/users/me'),
          getAllPages<User>('/api/users/directory'),
          getAllPages<ConversationListItem>('/api/conversations'),
          getAllPages<Presence>('/api/users/statuses'),
        ])
        if (cancelled) return
        setCurrentUser(me.data)
        setUsers(directory)
        setConversations(chats)
        setPresence(Object.fromEntries(statuses.map((item) => [item.userId, item.status])))
      } catch (requestError) {
        if (cancelled) return
        if (axios.isAxiosError(requestError) && requestError.response?.status === 401) {
          onUnauthorized()
        } else {
          onError(getErrorMessage(requestError))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadMessenger()
    return () => { cancelled = true }
  }, [token, onUnauthorized, setLoading, onError])

  useEffect(() => {
    if (!token) return

    const refreshPresence = async () => {
      try {
        const statuses = await getAllPages<Presence>('/api/users/statuses')
        setPresence(Object.fromEntries(statuses.map((item) => [item.userId, item.status])))
      } catch (requestError) {
        onError(getErrorMessage(requestError))
      }
    }
    const interval = window.setInterval(() => { void refreshPresence() }, 30_000)
    return () => window.clearInterval(interval)
  }, [token, onError])

  const markRead = useCallback(async (conversationId: number, messageId: number) => {
    try {
      await api.post(`/api/conversations/${conversationId}/read`, { messageId })
      setConversations((items) => items.map((item) => (
        item.id === conversationId ? { ...item, unreadCount: 0 } : item
      )))
    } catch (requestError) {
      onError(getErrorMessage(requestError))
    }
  }, [onError])

  const openConversation = useCallback(async (user: User): Promise<ConversationListItem> => {
    const existing = conversations.find((item) => item.otherUser.id === user.id)
    if (existing) return existing

    const response = await api.post<Conversation>('/api/conversations', { companionId: user.id })
    const conversation = response.data
    const item: ConversationListItem = {
      id: conversation.id,
      otherUser: user,
      lastMessage: conversation.lastMessage,
      lastMessageAt: conversation.lastMessageAt,
      unreadCount: 0,
    }
    setConversations((items) => {
      const current = items.find((entry) => entry.id === item.id)
      return current ? items.map((entry) => entry.id === item.id ? item : entry) : [item, ...items]
    })
    return item
  }, [conversations])

  const handleIncomingMessage = useCallback((message: Message) => {
    setConversations((items) => items.map((item) => item.id === message.conversationId
      ? {
          ...item,
          lastMessage: message,
          lastMessageAt: message.createdAt,
          unreadCount: message.sender.id !== currentUserRef.current?.id
            && selectedConversationIdRef.current !== message.conversationId
            ? item.unreadCount + 1
            : item.unreadCount,
        }
      : item))
    if (message.sender.id !== currentUserRef.current?.id
      && selectedConversationIdRef.current === message.conversationId) {
      void markRead(message.conversationId, message.id)
    }
  }, [markRead])

  const handleSentMessage = useCallback((message: Message) => {
    setConversations((items) => items.map((item) => item.id === message.conversationId
      ? { ...item, lastMessage: message, lastMessageAt: message.createdAt }
      : item))
  }, [])

  return {
    currentUser,
    users,
    conversations,
    presence,
    markRead,
    openConversation,
    handleIncomingMessage,
    handleSentMessage,
  }
}
