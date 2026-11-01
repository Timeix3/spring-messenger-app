import { useState, type FormEvent } from 'react'
import { getErrorMessage } from '../lib/api'

type AuthMode = 'login' | 'register'

export function AuthPage({
  onAuthenticate,
}: {
  onAuthenticate: (mode: AuthMode, username: string, password: string, displayName?: string) => Promise<void>
}) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await onAuthenticate(mode, username, password, name.trim() || undefined)
    } catch (requestError) {
      setError(getErrorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  function changeMode(nextMode: AuthMode) {
    setMode(nextMode)
    setError('')
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="brand-mark" aria-hidden="true">м</div>
        <p className="eyebrow">ВАШИ РАЗГОВОРЫ — В ОДНОМ МЕСТЕ</p>
        <h1>{mode === 'login' ? 'С возвращением' : 'Создать аккаунт'}</h1>
        <p className="auth-description">
          {mode === 'login' ? 'Войдите, чтобы продолжить общение.' : 'Зарегистрируйтесь, чтобы начать общаться.'}
        </p>

        <div className="auth-tabs" role="tablist" aria-label="Способ входа">
          <button
            className={mode === 'login' ? 'active' : ''}
            onClick={() => changeMode('login')}
            role="tab"
            aria-selected={mode === 'login'}
            type="button"
          >
            Войти
          </button>
          <button
            className={mode === 'register' ? 'active' : ''}
            onClick={() => changeMode('register')}
            role="tab"
            aria-selected={mode === 'register'}
            type="button"
          >
            Регистрация
          </button>
        </div>

        <form className="auth-form" onSubmit={submit}>
          {mode === 'register' && (
            <label>
              Имя
              <input
                autoComplete="name"
                maxLength={64}
                onChange={(event) => setName(event.target.value)}
                placeholder="Как к вам обращаться"
                value={name}
              />
            </label>
          )}
          <label>
            Логин
            <input
              autoComplete="username"
              maxLength={32}
              minLength={mode === 'register' ? 3 : undefined}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="например, alex_01"
              required
              value={username}
            />
          </label>
          <label>
            Пароль
            <input
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              minLength={mode === 'register' ? 8 : undefined}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={mode === 'register' ? 'Не менее 8 символов' : 'Введите пароль'}
              required
              type="password"
              value={password}
            />
          </label>
          {error && <div className="form-error" role="alert">{error}</div>}
          <button className="primary-button auth-submit" disabled={submitting} type="submit">
            {submitting ? 'Подождите…' : mode === 'login' ? 'Войти в мессенджер' : 'Создать аккаунт'}
          </button>
        </form>

        <p className="auth-footnote">Сообщения передаются через защищённое соединение с сервером.</p>
      </section>
    </main>
  )
}
