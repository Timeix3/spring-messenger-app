import { useCallback, useState } from 'react'
import { api, getErrorMessage } from '../lib/api'
import type { AuthResponse } from '../types'

const TOKEN_KEY = 'messenger-token'

export function useAuth(onError: (message: string) => void) {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY))
  const [loading, setLoading] = useState(() => Boolean(sessionStorage.getItem(TOKEN_KEY)))

  async function authenticate(
    mode: 'login' | 'register',
    username: string,
    password: string,
    displayName?: string,
  ): Promise<void> {
    const path = mode === 'login' ? '/api/auth/login' : '/api/auth/register'
    const payload = mode === 'login'
      ? { username, password }
      : { username, password, displayName }
    const response = await api.post<AuthResponse>(path, payload)
    sessionStorage.setItem(TOKEN_KEY, response.data.accessToken)
    setLoading(true)
    setToken(response.data.accessToken)
  }

  async function logout(): Promise<void> {
    try {
      await api.post('/api/auth/logout')
    } catch (error) {
      onError(getErrorMessage(error))
    } finally {
      sessionStorage.removeItem(TOKEN_KEY)
      setToken(null)
    }
  }

  const expireSession = useCallback((): void => {
    sessionStorage.removeItem(TOKEN_KEY)
    setLoading(false)
    setToken(null)
  }, [])

  return { token, loading, setLoading, authenticate, logout, expireSession }
}
