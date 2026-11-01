import { useEffect, useRef, useState } from 'react'
import { api, getErrorMessage } from '../lib/api'
import type { Message, PageResponse } from '../types'

export function useChatMessages(
  conversationId: number | null,
  markRead: (conversationId: number, messageId: number) => Promise<void>,
  onSent: (message: Message) => void,
  onError: (message: string) => void,
) {
  const [messages, setMessages] = useState<Message[]>([])
  const [loadedConversationId, setLoadedConversationId] = useState<number | null>(null)
  const [messagePage, setMessagePage] = useState(0)
  const [hasOlderMessages, setHasOlderMessages] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [sending, setSending] = useState(false)
  const selectedIdRef = useRef(conversationId)
  const sentHandlerRef = useRef(onSent)
  const errorHandlerRef = useRef(onError)

  useEffect(() => {
    selectedIdRef.current = conversationId
  }, [conversationId])

  useEffect(() => {
    sentHandlerRef.current = onSent
    errorHandlerRef.current = onError
  }, [onSent, onError])

  useEffect(() => {
    if (conversationId === null) return

    let cancelled = false
    const id = conversationId

    async function loadLatestMessages() {
      try {
        const response = await api.get<PageResponse<Message>>(
          `/api/conversations/${id}/messages`,
          { params: { page: 0, size: 50 } },
        )
        if (cancelled) return
        const ordered = [...response.data.content].reverse()
        setMessages(ordered)
        setMessagePage(0)
        setHasOlderMessages(!response.data.last)
        const lastMessage = ordered.at(-1)
        if (lastMessage) await markRead(id, lastMessage.id)
      } catch (requestError) {
        if (!cancelled) errorHandlerRef.current(getErrorMessage(requestError))
      } finally {
        if (!cancelled) setLoadedConversationId(id)
      }
    }

    void loadLatestMessages()
    return () => { cancelled = true }
  }, [conversationId, markRead])

  function receiveMessage(message: Message): void {
    if (selectedIdRef.current !== message.conversationId) return
    setMessages((items) => items.some((item) => item.id === message.id)
      ? items
      : [...items, message].sort((a, b) => a.id - b.id))
  }

  async function loadOlderMessages(): Promise<void> {
    if (conversationId === null || loadedConversationId !== conversationId || loadingOlder || !hasOlderMessages) return
    const id = conversationId
    const nextPage = messagePage + 1
    setLoadingOlder(true)
    try {
      const response = await api.get<PageResponse<Message>>(
        `/api/conversations/${id}/messages`,
        { params: { page: nextPage, size: 50 } },
      )
      if (selectedIdRef.current !== id) return
      setMessages((items) => {
        const existingIds = new Set(items.map((message) => message.id))
        const older = response.data.content.filter((message) => !existingIds.has(message.id)).reverse()
        return [...older, ...items]
      })
      setMessagePage(nextPage)
      setHasOlderMessages(!response.data.last)
    } catch (requestError) {
      errorHandlerRef.current(getErrorMessage(requestError))
    } finally {
      setLoadingOlder(false)
    }
  }

  async function sendMessage(content: string): Promise<boolean> {
    if (conversationId === null || !content.trim() || sending) return false
    const id = conversationId
    setSending(true)
    try {
      const response = await api.post<Message>(`/api/conversations/${id}/messages`, { content: content.trim() })
      if (selectedIdRef.current === id) {
        receiveMessage(response.data)
        sentHandlerRef.current(response.data)
      }
      return true
    } catch (requestError) {
      errorHandlerRef.current(getErrorMessage(requestError))
      return false
    } finally {
      setSending(false)
    }
  }

  return {
    messages: loadedConversationId === conversationId ? messages : [],
    loading: conversationId !== null && loadedConversationId !== conversationId,
    loadingOlder,
    hasOlderMessages,
    sending,
    loadOlderMessages,
    sendMessage,
    receiveMessage,
  }
}
