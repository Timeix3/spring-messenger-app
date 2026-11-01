# Spring Messenger

Веб-мессенджер: REST API и WebSocket на Spring Boot, React-интерфейс с авторизацией, каталогом пользователей, личными диалогами, непрочитанными сообщениями и постраничной историей.

## Запуск

1. Скопируйте `.env.example` в `.env` и замените демонстрационные пароли и JWT-ключ. Для JWT нужен Base64-ключ длиной не менее 32 байт; сгенерировать его в PowerShell можно так:

   ```powershell
   $bytes = New-Object byte[] 64
   [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
   [Convert]::ToBase64String($bytes)
   ```

2. Запустите все сервисы из корня проекта:

   ```powershell
   docker compose up --build
   ```

После сборки приложение будет доступно на `http://localhost:5173`, API — на `http://localhost:8080`. PostgreSQL и Redis запускаются вместе с приложением. Для остановки используйте `docker compose down`.

Файл `.env` уже исключен из Git и автоматически используется Docker Compose. Не добавляйте его в коммиты. Для другого адреса API измените `VITE_API_URL` в `.env` и пересоберите frontend.

## Основные маршруты API

- `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`
- `GET /api/users/me`, `GET /api/users/directory`, `GET /api/users/search`
- `GET /api/conversations`, `POST /api/conversations`
- `GET /api/conversations/{id}/messages`, `POST /api/conversations/{id}/messages`
- WebSocket/STOMP: `/ws`, сообщения диалога `/topic/conversations/{id}`

`GET /api/users/directory` возвращает пользователей, кроме текущего, с обычной пагинацией. Отдельный маршрут сохраняет прежнее поведение поиска, при котором пустой запрос не возвращает список пользователей.
