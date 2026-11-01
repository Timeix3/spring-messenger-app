# Backend

Backend — Spring Boot REST API и WebSocket/STOMP-сервер мессенджера. Он отвечает за аутентификацию и авторизацию, пользователей, личные диалоги, сохранение и доставку сообщений, отметки прочтения и присутствие пользователей.

## Стек и устройство

- Java 21, Spring Boot 4, Maven.
- Spring MVC, Spring Security, Spring WebSocket/STOMP и Bean Validation.
- Spring Data JPA / Hibernate и PostgreSQL.
- Flyway: миграции находятся в `src/main/resources/db/migration`.
- Redis: присутствие и WebSocket-сессии, а также rate limiting.
- JJWT для access-токенов; MapStruct и Lombok.
- springdoc-openapi / Swagger UI.
- JUnit и Testcontainers для интеграционных тестов.

Код разделён на контроллеры, сервисы, репозитории, сущности, DTO, мапперы и конфигурацию. При старте Flyway применяет миграции, а Hibernate проверяет соответствие модели схеме (`ddl-auto: validate`). Основные таблицы: `users`, `conversations` и `messages`. Диалог представляет собой личную переписку пары пользователей.

Сообщение сохраняется в PostgreSQL, а после успешного коммита транзакции публикуется в соответствующую STOMP-тему. Redis используется для временных данных, не являющихся историей сообщений.

## Запуск и конфигурация

Предпочтительный способ поднять весь стек — из корня проекта:

```powershell
Copy-Item .env.example .env
# Замените демонстрационные пароли и APP_JWT_SECRET_BASE64 в .env
docker compose up --build -d
```

Compose запускает PostgreSQL и Redis с health check, затем backend и frontend. В контейнере backend доступен на порту `8080`. Для просмотра логов: `docker compose logs -f backend`.

Основные параметры backend задаются переменными окружения:

| Переменная | Назначение |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Учётные данные PostgreSQL |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` / `SPRING_DATA_REDIS_PASSWORD` | Подключение к Redis |
| `APP_JWT_SECRET_BASE64` | Base64-секрет JWT; требуется ключ достаточной длины |

В `application.yml` также заданы срок жизни access-токена (15 минут), конфигурация Flyway, пул соединений и пути OpenAPI. Не используйте демонстрационные пароли и JWT-ключ из `.env.example` в production.

## REST API

Базовый адрес при локальном запуске — `http://localhost:8080`. Кроме регистрации и входа, все перечисленные ниже REST-маршруты требуют заголовок `Authorization: Bearer <accessToken>`. Пагинированные ответы имеют формат `content`, `page`, `size`, `totalElements`, `totalPages`, `last`.

### Аутентификация — `/api/auth`

| Метод и путь | Описание | Успешный ответ |
|---|---|---|
| `POST /api/auth/register` | Создать пользователя. Тело: `{"username":"alice","password":"...","displayName":"Alice"}`. Имя пользователя: 3–32 символа, латиница, цифры и `_`; пароль: 8–72 символа; отображаемое имя — до 64 символов. | `201 Created`, объект с `tokenType`, `accessToken`, `expiresInSeconds`, `userId`, `username`, `displayName` |
| `POST /api/auth/login` | Войти. Тело: `{"username":"alice","password":"..."}`. | `200 OK`, объект токена и пользователя |
| `POST /api/auth/logout` | Отозвать текущий JWT через blacklist. Передайте bearer-токен. | `204 No Content` |

### Пользователи и присутствие

| Метод и путь | Описание |
|---|---|
| `GET /api/users/me` | Профиль текущего пользователя |
| `GET /api/users/{userId}` | Профиль пользователя по ID |
| `GET /api/users/directory?page=0&size=100` | Каталог пользователей без текущего пользователя |
| `GET /api/users/search?q=alice&page=0&size=20` | Поиск пользователей; пустой `q` не используется как каталог |
| `GET /api/users/statuses?page=0&size=50` | Пагинированные статусы присутствия, отсортированные по имени |
| `GET /api/users/statuses/{userId}` | Статус присутствия пользователя |

Параметры `page` и `size` необязательны; значения по умолчанию приведены в таблице.

### Диалоги и сообщения

| Метод и путь | Описание | Успешный ответ |
|---|---|---|
| `GET /api/conversations?page=0&size=20` | Список диалогов текущего пользователя | `200 OK`, страница диалогов |
| `POST /api/conversations` | Создать/получить личный диалог. Тело: `{"companionId":2}` | `201 Created`, данные диалога |
| `GET /api/conversations/{conversationId}` | Получить диалог, если текущий пользователь — его участник | `200 OK` |
| `POST /api/conversations/{conversationId}/read` | Отметить сообщения прочитанными. Тело: `{"messageId":123}` | `204 No Content` |
| `GET /api/conversations/{conversationId}/messages?page=0&size=50` | Получить историю сообщений с пагинацией | `200 OK`, страница сообщений |
| `POST /api/conversations/{conversationId}/messages` | Отправить сообщение. Тело: `{"content":"Привет!"}`; длина — 1–4000 символов | `201 Created`, созданное сообщение |

Доступ к операциям проверяется по членству в диалоге. Ограничение отправки — не более 10 сообщений за 5 секунд на пользователя; REST-запрос при превышении получает `429 Too Many Requests`.

### Ошибки и интерактивная документация

Ошибки API возвращаются централизованным обработчиком в JSON-формате с временной меткой, сообщением и дополнительными деталями. Среди ожидаемых статусов — `400` (валидация), `401` (аутентификация), `403` (нет доступа), `404` (не найдено) и `429` (ограничение частоты).

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## WebSocket / STOMP

Точка подключения: `/ws` (обычный WebSocket и SockJS). Токен передаётся как bearer в HTTP handshake или в STOMP `CONNECT` header `Authorization: Bearer <accessToken>`; при подключении через SockJS также поддерживается query-параметр `token`. Для STOMP-клиента используется префикс отправки `/app`, для пользовательских подписок — `/user`.

| Операция | Destination | Назначение |
|---|---|---|
| Подписка | `/topic/conversations/{conversationId}` | Получать новые сообщения диалога |
| Отправка | `/app/conversations/{conversationId}/messages` | Отправить payload `{"content":"Привет!"}` |
| Подписка | `/user/queue/errors` | Получать персональные ошибки доставки, например о превышении rate limit |
| Отправка | `/app/presence/heartbeat` | Heartbeat присутствия; содержимое не требуется |

Пользователь может подписаться на тему или отправлять сообщения только для диалога, участником которого он является. Для обновления статуса heartbeat следует периодически отправлять по активному соединению; TTL присутствия — две минуты. Успешная отправка через WebSocket сохраняет сообщение так же, как REST, а затем рассылает его подписчикам темы.

## Проверки и CI

Из каталога `backend`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:check
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

`verify` выполняет сборку и тесты; для интеграционных тестов используется Testcontainers, поэтому требуется доступный Docker. GitHub Actions запускает проверку Spotless и `verify` при `push` и `pull_request` (Java 21, Temurin).
