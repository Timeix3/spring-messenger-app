import axios from 'axios'
import type { PageResponse } from '../types'

export const apiBaseUrl = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export const api = axios.create({ baseURL: apiBaseUrl })

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('messenger-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data: unknown = error.response?.data
    if (data && typeof data === 'object') {
      const body = data as Record<string, unknown>
      if (typeof body.message === 'string') return body.message
      if (typeof body.error === 'string') return body.error
    }
    if (error.code === 'ERR_NETWORK') return 'Не удалось подключиться к серверу.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Произошла неизвестная ошибка'
}

export async function getAllPages<T>(
  path: string,
  params: Record<string, string | number> = {},
): Promise<T[]> {
  const items: T[] = []
  let page = 0
  let last = false

  while (!last) {
    const response = await api.get<PageResponse<T>>(path, { params: { ...params, page, size: 100 } })
    items.push(...response.data.content)
    last = response.data.last
    page += 1
  }

  return items
}
