import type { User } from '../types'

export function displayName(user: User): string {
  return user.displayName?.trim() || user.username
}

export function avatarLetters(user: User): string {
  return displayName(user).slice(0, 2).toLocaleUpperCase('ru')
}

export function formatTime(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' })
}

export function formatMessageTime(value: string): string {
  return new Date(value).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}
