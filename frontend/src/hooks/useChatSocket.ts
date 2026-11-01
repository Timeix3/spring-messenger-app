import { useEffect, useRef, useState } from 'react'
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { apiBaseUrl, getErrorMessage } from '../lib/api'
import type { ConversationListItem, Message } from '../types'

export function useChatSocket(
  token: string | null,
  conversations: ConversationListItem[],
  onMessage: (message: Message) => void,
  onError: (message: string) => void,
) {
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const subscriptionsRef = useRef(new Map<string, StompSubscription>())
  const messageHandlerRef = useRef(onMessage)
  const errorHandlerRef = useRef(onError)

  useEffect(() => {
    messageHandlerRef.current = onMessage
    errorHandlerRef.current = onError
  }, [onMessage, onError])

  useEffect(() => {
    if (!token) return

    const subscriptions = subscriptionsRef.current
    const client = new Client({
      webSocketFactory: () => new SockJS(`${apiBaseUrl}/ws?token=${encodeURIComponent(token)}`),
      reconnectDelay: 5000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        setConnected(true)
        client.publish({ destination: '/app/presence/heartbeat', body: '{}' })
        if (!subscriptions.has('queue:errors')) {
          subscriptions.set('queue:errors', client.subscribe('/user/queue/errors', (frame) => {
            try {
              const body = JSON.parse(frame.body) as { message?: string }
              errorHandlerRef.current(body.message || 'Не удалось отправить сообщение')
            } catch (parseError) {
              errorHandlerRef.current(`Ошибка WebSocket: ${getErrorMessage(parseError)}`)
            }
          }))
        }
      },
      onWebSocketClose: () => {
        setConnected(false)
        subscriptions.clear()
      },
      onStompError: (frame) => {
        errorHandlerRef.current(frame.headers.message || 'Ошибка соединения WebSocket')
      },
    })

    clientRef.current = client
    void client.activate()
    const heartbeat = window.setInterval(() => {
      if (client.connected) {
        client.publish({ destination: '/app/presence/heartbeat', body: '{}' })
      }
    }, 25_000)

    return () => {
      window.clearInterval(heartbeat)
      setConnected(false)
      subscriptions.clear()
      clientRef.current = null
      void client.deactivate()
    }
  }, [token])

  useEffect(() => {
    const client = clientRef.current
    if (!token || !connected || !client?.connected) return

    const desired = new Set(conversations.map((conversation) => `conversation:${conversation.id}`))
    for (const [key, subscription] of subscriptionsRef.current) {
      if (key.startsWith('conversation:') && !desired.has(key)) {
        subscription.unsubscribe()
        subscriptionsRef.current.delete(key)
      }
    }

    for (const conversation of conversations) {
      const key = `conversation:${conversation.id}`
      if (subscriptionsRef.current.has(key)) continue
      const subscription = client.subscribe(`/topic/conversations/${conversation.id}`, (frame) => {
        try {
          messageHandlerRef.current(JSON.parse(frame.body) as Message)
        } catch (parseError) {
          errorHandlerRef.current(`Не удалось обработать входящее сообщение: ${getErrorMessage(parseError)}`)
        }
      })
      subscriptionsRef.current.set(key, subscription)
    }
  }, [token, connected, conversations])

  return connected
}
