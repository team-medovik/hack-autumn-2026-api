# API оценки риска непродления услуг

Сервис принимает снимок биллинга с клиентами, услугами и транзакциями, рассчитывает риск для услуг и клиентов и отдаёт дашборд, список клиентов и карточки с объяснением оценки. Результат строится по последнему загруженному снимку, текущим настройкам модели и выбранной дате расчёта. Числа в ответах не зашиты заранее: загрузка другого снимка или изменение настроек меняет результат.

В репозитории есть исходный код, [Dockerfile](Dockerfile), [docker-compose.yml](docker-compose.yml), [генератор тестового набора](scripts/generate.py) и эта инструкция. Compose рассчитан на окружение команды: он запускает только API. PostgreSQL и внешняя Docker-сеть backend подготавливаются отдельно, а JAR собирается до команды compose up.

## Запуск из чистого клона

Команды ниже рассчитаны на Linux или WSL2 с Linux-контейнерами. Нужны Docker Engine с Compose, JDK 25, Python 3.10+ для генерации набора и curl для запросов. Порты 33000 и 33001 должны быть свободны. Все команды выполняются из корня репозитория.

1. Подготовьте сеть и PostgreSQL. Если в сети backend уже доступен PostgreSQL с именем postgres, используйте его и пропустите docker run. Пароль в примере локальный; укажите такой же в .env.

~~~sh
docker network inspect backend >/dev/null 2>&1 || docker network create backend
docker run -d --name hack2026-postgres \
  --network backend --network-alias postgres \
  -e POSTGRES_DB=hack-autumn-2026 \
  -e POSTGRES_USER=hack2026 \
  -e POSTGRES_PASSWORD=local-demo-password \
  -v hack2026-postgres:/var/lib/postgresql/data \
  postgres:17-alpine
docker exec hack2026-postgres pg_isready -U hack2026 -d hack-autumn-2026
~~~

Если pg_isready ответил, что база ещё запускается, повторите последнюю команду через несколько секунд. База хранит данные в Docker-томе hack2026-postgres. Схему приложения при старте создают миграции Flyway.

2. Подготовьте конфиги, которых нет в Git. Compose монтирует runtime/users.env как /config/users.env. В его текущей конфигурации также указан абсолютный путь для логов, поэтому каталог должен существовать на Docker-хосте.

~~~sh
mkdir -p runtime
cp src/main/resources/.env.example .env
cp src/main/resources/users.env.example runtime/users.env
sudo mkdir -p /home/kinoko/logs/teamup/hack-autumn-2026-api
openssl rand -base64 32
~~~

Откройте .env и задайте реальные значения как минимум для следующих строк. Скопируйте результат openssl в APP_AUTH_JWT_SECRET: после декодирования ключ должен содержать не меньше 32 байт.

~~~dotenv
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL='jdbc:postgresql://postgres:5432/hack-autumn-2026'
SPRING_DATASOURCE_USERNAME='hack2026'
SPRING_DATASOURCE_PASSWORD='local-demo-password'
APP_AUTH_JWT_SECRET='сюда-вставить-результат-openssl'
APP_AUTH_SECURE_COOKIE='false'
APP_AUTH_OIDC_ENABLED='false'
~~~

В runtime/users.env замените пароль администратора. Для первого запуска достаточно этого блока; пример USERS_1 с незаполненным Keycloak subject закомментируйте до настройки SSO.

~~~dotenv
USERS_0_LOGIN='admin'
USERS_0_PASSWORD='придумайте-длинный-пароль'
USERS_0_ROLE='ADMIN'
USERS_0_STATUS='ACTIVE'
~~~

3. Соберите JAR и запустите API. Dockerfile копирует уже собранный файл из build/libs, поэтому одной команды compose up из чистого клона недостаточно. На Windows JAR можно собрать через gradlew.bat bootJar; остальные команды этого раздела выполняйте в Linux/WSL2.

~~~sh
bash ./gradlew bootJar
docker compose config --quiet
docker compose up --build -d
docker compose logs --tail=100 hack-autumn-2026-api
curl -fsS http://localhost:33001/actuator/health
~~~

Приложение слушает порт 33000, health и Prometheus — порт 33001. Swagger UI: http://localhost:33000/api/v1/docs. В Docker Compose имя сервиса — hack-autumn-2026-api. Для остановки API выполните docker compose down; PostgreSQL из шага 1 запущен отдельно и этой командой не останавливается.

## Файлы конфигурации и учётные записи

- [application.yml](src/main/resources/application.yml) задаёт общие параметры: порт, срок жизни токенов, URL Swagger и выключенный по умолчанию OIDC.
- [application-prod.yml](src/main/resources/application-prod.yml) берёт подключение к PostgreSQL из SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME и SPRING_DATASOURCE_PASSWORD. Compose выбирает профиль prod через .env.
- [application-dev.yml](src/main/resources/application-dev.yml) содержит адрес базы команды и не подходит для автономного запуска без замены параметров. Если запускать JAR или IDE без профиля, выбирается dev. Для отдельного запуска задайте SPRING_PROFILES_ACTIVE=prod и те же переменные базы.
- [.env.example](src/main/resources/.env.example) — шаблон корневого .env для Compose. Помимо базы и JWT здесь можно задать APP_AUTH_ACCESS_TOKEN_TTL (по умолчанию 15m), APP_AUTH_REFRESH_TOKEN_TTL (30d), параметры OIDC и USERS_ENV_DIR, если users.env лежит в другом каталоге.
- [users.env.example](src/main/resources/users.env.example) — шаблон runtime/users.env. Для каждого сотрудника используются USERS_0_*, USERS_1_* и так далее. LOGIN нормализуется к нижнему регистру; ROLE — ADMIN или EMPLOYEE, STATUS — ACTIVE или DISABLED. Пользователь с PASSWORD может войти по логину и паролю; пользователь с OIDC_ISSUER и OIDC_SUBJECT может войти через SSO без пароля. Файл читается при запуске и проверяется на изменения каждые 5 секунд. Удаление записи из файла удаляет управляемую им учётную запись.
- APP_AUTH_SECURE_COOKIE должен быть false при локальном HTTP и true за HTTPS. .env и runtime/users.env содержат секреты и исключены из Git.

В серверном деплое [workflow](.github/workflows/deploy.yml) получает .env из секрета ENV_PRODUCTION, а users.env хранит вне каталога API, который заменяется при выкладке. Не подставляйте примерные пароли и ключи в публичное окружение.

Для запуска без Compose из IDE или через Gradle задайте SPRING_PROFILES_ACTIVE=prod, APP_AUTH_JWT_SECRET и переменные SPRING_DATASOURCE_* для PostgreSQL, доступного с хоста. Скопируйте users.env.example в src/main/resources/users.env: при рабочем каталоге в корне проекта приложение читает этот путь. Затем запустите Hack2026ApplicationKt из IDE или bash ./gradlew bootRun. Сетевая база из Docker-команды выше не опубликована на localhost; для такого запуска нужен отдельный доступный PostgreSQL или опубликованный порт.

## Загрузка исходного набора и проверка результата

[Генератор](scripts/generate.py) воспроизводит синтетический набор по умолчанию: 10 клиентов, 30 услуг и 100 транзакций, seed 42, опорная дата 2026-10-19T15:00:00Z. Он записывает data.json и 002_seed.sql. API принимает JSON через HTTP; SQL-файл для запуска API не требуется. Если у жюри есть исходный data.json из материалов кейса, можно использовать его вместо сгенерированного файла.

~~~sh
python3 scripts/generate.py --out build/fixture
~~~

В Swagger UI вызовите POST /api/v1/auth/login с логином admin и паролем из runtime/users.env. Скопируйте поле accessToken. В Swagger кнопка Authorize позволяет использовать его для последующих запросов. Для команд ниже вставьте токен в переменную TOKEN.

~~~sh
export TOKEN='вставьте-accessToken'
curl -fsS -X PUT http://localhost:33000/api/v1/settings/calculation-date \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  --data '{"mode":"fixed","fixedAt":"2026-10-19T15:00:00Z"}'
curl -fsS -X POST 'http://localhost:33000/api/v1/snapshots?fileName=data.json' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  --data-binary @build/fixture/data.json
curl -fsS -H "Authorization: Bearer $TOKEN" http://localhost:33000/api/v1/dashboard
curl -fsS -H "Authorization: Bearer $TOKEN" http://localhost:33000/api/v1/clients/1
~~~

Дата fixed сохраняет воспроизводимость примера. В режиме current риск меняется по мере приближения сроков услуг. GET /api/v1/snapshots показывает историю загрузок; последний загруженный снимок становится активным. Формат — JSON с массивами accounts, services и transactions; принимается также вложенный вариант clients[].services. Связи по ownerId и serviceId проверяются при импорте, максимальный размер файла — 20 МиБ. Денежные значения в исходном снимке — целые евроценты.

Чтобы убедиться, что оценка зависит от данных, сохраните ответ GET /api/v1/clients/1, измените входной признак у первой услуги тестового набора и загрузите новый снимок:

~~~sh
python3 -c 'import json,sys; src,dst=sys.argv[1:]; data=json.load(open(src,encoding="utf-8")); data["services"][0]["autoProlong"]=True; open(dst,"w",encoding="utf-8").write(json.dumps(data,ensure_ascii=False))' \
  build/fixture/data.json build/fixture/changed.json
curl -fsS -X POST 'http://localhost:33000/api/v1/snapshots?fileName=changed.json' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  --data-binary @build/fixture/changed.json
curl -fsS -H "Authorization: Bearer $TOKEN" http://localhost:33000/api/v1/clients/1
~~~

У клиента 1 оценка изменится, потому что его услуга перестанет удовлетворять условию признака «истекает без автопродления». Для возврата к исходному состоянию повторно загрузите build/fixture/data.json. По аналогии можно менять балансы, сроки, статусы услуг и веса модели; вычисление идёт по новым входным значениям.

## Как устроена модель

[RiskEngine](src/main/kotlin/ru/medovik/hack2026/api/service/risk/RiskEngine.kt) получает снимок, настройки и момент расчёта. [BalanceSimulator](src/main/kotlin/ru/medovik/hack2026/api/service/risk/BalanceSimulator.kt) определяет доступные средства и стоимость продлений в заданном горизонте. Каждый признак возвращает применимость, силу от 0 до 1, текст причины и факты. Вклад равен силе, умноженной на настроенный вес; сумма вкладов услуги ограничивается 100. Оценка клиента — максимум оценок его активных услуг. Пороги по умолчанию: medium от 40, high от 70; начальный горизонт в базе — 5 суток. Это экспертный индекс внимания, а не статистически обученная вероятность оттока.

Сейчас зарегистрированы шесть признаков: ожидание оплаты, окончание срока без автопродления, нехватка средств, ожидание оплаты при нехватке средств, приостановка услуги и превышение доступных бонусов над денежным балансом. Для blocked и deleted оценка услуги отсутствует. Транзакции сохраняются и показываются как часть исходного снимка, но текущие формулы риска используют балансы, статусы, цены и сроки услуг.

Поток данных: POST /api/v1/snapshots → [SnapshotService](src/main/kotlin/ru/medovik/hack2026/api/service/snapshot/SnapshotService.kt) → PostgreSQL billing_snapshots → RiskEngine → [DashboardService](src/main/kotlin/ru/medovik/hack2026/api/service/dashboard/DashboardService.kt) → REST API. Flyway применяет миграции, jOOQ работает с таблицами, JWT защищает прикладные маршруты. DashboardService читает последний снимок и пересчитывает его с актуальными настройками и датой при запросе.

## Настройки модели через API

GET /api/v1/settings/model возвращает version, действующие values, defaults, featureDefinitions и распределение оценок. Начальная запись создаётся миграцией [V3__create_model_settings.sql](src/main/resources/db/migration/V3__create_model_settings.sql). Для частичного изменения используйте PATCH /api/v1/model/settings и передайте текущую version из GET, например:

~~~json
{
  "version": 1,
  "changes": {
    "factorWeights": {
      "expiringWithoutAutoRenewal": 60
    },
    "finance": {
      "bonusesAsMoney": false,
      "balanceLossFactor": 0.55
    }
  }
}
~~~

При успешном сохранении версия увеличится; при устаревшей версии сервер вернёт конфликт. Веса — целые числа 0–100. Пороги должны удовлетворять 0 < medium < high <= 100. У finance есть bonusesAsMoney, referralBalanceAsMoney и balanceLossFactor от 0 до 1 с шагом 0.05. У forecast есть horizonDays от 1 до 30 и recalculationIntervalMinutes от 1 до 60. PUT /api/v1/settings/model сохраняет полный объект values; PATCH меняет только переданные поля. [PatchFinanceSettings.kt](src/main/kotlin/ru/medovik/hack2026/api/dto/settings/PatchFinanceSettings.kt) описывает именно частичное изменение финансовых параметров.

## Как добавить новый признак риска

1. Добавьте реализацию [RiskFeature](src/main/kotlin/ru/medovik/hack2026/api/service/risk/feature/RiskFeature.kt) в каталог service/risk/feature/impl. Образец — [ExpiryWithoutAutoRenew.kt](src/main/kotlin/ru/medovik/hack2026/api/service/risk/feature/impl/ExpiryWithoutAutoRenew.kt). Задайте уникальный code, реализуйте evaluate(context). Если условие не выполнено, верните FeatureEvaluation.notApplicable(); иначе укажите силу в диапазоне 0–1, объяснение и evidence с использованными фактами. В context доступны клиент, услуга, рассчитанные средства, настройки и момент расчёта.
2. Добавьте RegisteredRiskFeature в [RiskFeatureRegistry](src/main/kotlin/ru/medovik/hack2026/api/service/risk/feature/RiskFeatureRegistry.kt): экземпляр признака, FeatureDefinition с уникальным key в camelCase, названием и описанием, затем целый вес по умолчанию 0–100. Порядок записей задаёт порядок показа в настройках. Реестр автоматически передаёт признак движку, добавляет его в featureDefinitions и карту весов. Для старой записи настроек недостающий вес берётся из реестра.
3. Если карточке клиента нужна специальная формулировка причины, добавьте code в reasonId и reasonLabel [DashboardService](src/main/kotlin/ru/medovik/hack2026/api/service/dashboard/DashboardService.kt). Без этого будет общее название «Фактор риска». Когда признак требует новое поле во входном JSON, дополните DTO снимка и контракт [unified.yaml](src/main/resources/openapi/unified.yaml); для признака на существующих полях динамический контракт весов менять не нужно.
4. Загрузите два снимка с разным значением поля, от которого зависит новый признак, и сравните карточку клиента и GET /api/v1/settings/model. Изменение должно влиять на вклад признака и итоговую оценку.

## Включение SSO через Keycloak

1. В Keycloak создайте realm и confidential client с Authorization Code / Standard Flow. Разрешённый redirect URI для публичного адреса API: https://ВАШ-ДОМЕН/api/v1/auth/oidc/keycloak/callback. Issuer вида https://KEYCLOAK/realms/ВАШ-REALM должен быть доступен контейнеру API для OIDC discovery. У клиента должны быть scopes openid, email и profile.
2. В корневом .env заполните параметры ниже и перезапустите API командой docker compose up -d. APP_AUTH_OIDC_SUCCESS_REDIRECT_URI — относительный путь фронтенда на том же origin, что и API. За HTTPS используйте secure cookie.

~~~dotenv
APP_AUTH_OIDC_ENABLED='true'
APP_AUTH_OIDC_ISSUER_URI='https://KEYCLOAK/realms/ВАШ-REALM'
APP_AUTH_OIDC_CLIENT_ID='hack-autumn-2026-api'
APP_AUTH_OIDC_CLIENT_SECRET='секрет-confidential-client'
APP_AUTH_OIDC_AUTO_LINK_BY_EMAIL='false'
APP_AUTH_OIDC_SUCCESS_REDIRECT_URI='/'
APP_AUTH_SECURE_COOKIE='true'
~~~

3. В runtime/users.env заранее заведите сотрудника и свяжите его с issuer и sub из Keycloak. Для обычного пользователя Keycloak subject совпадает с User ID. Для входа только через SSO строку PASSWORD не добавляйте.

~~~dotenv
USERS_1_LOGIN='analyst'
USERS_1_ROLE='EMPLOYEE'
USERS_1_STATUS='ACTIVE'
USERS_1_OIDC_ISSUER='https://KEYCLOAK/realms/ВАШ-REALM'
USERS_1_OIDC_SUBJECT='user-id-из-keycloak'
~~~

4. Откройте в браузере https://ВАШ-ДОМЕН/api/v1/auth/oidc/keycloak/login. После входа провайдер вернёт браузер на callback, API установит HttpOnly refresh_token и перенаправит на APP_AUTH_OIDC_SUCCESS_REDIRECT_URI. Фронтенд вызывает POST /api/v1/auth/refresh с cookie и получает accessToken для заголовка Authorization: Bearer. Cookie имеет SameSite=Strict и путь /api/v1/auth, поэтому фронтенд и API должны работать на одном origin.

По умолчанию APP_AUTH_OIDC_AUTO_LINK_BY_EMAIL=false: вход разрешён только для заранее привязанной пары issuer + sub. При true можно привязать существующую активную учётную запись по совпадающему email-логину, только если провайдер передал email_verified=true. Локальный вход по паролю остаётся доступен при включённом OIDC.


## Межсервисные секреты GitHub Actions

`ENV_PRODUCTION` остаётся основным набором настроек API. Его значение не нужно
перезаписывать при создании токенов. В API добавлены отдельные repository Secrets:

| Secret API | Совпадающий Secret ML-репозитория | Назначение |
|---|---|---|
| `APP_ML_SERVICE_TOKEN` | `ML_SERVICE_TOKEN` | Авторизация API в модели |
| `APP_GENERATOR_SERVICE_TOKEN` | `GENERATOR_SERVICE_TOKEN` | Чтение событий генератора |

При последующем деплое workflow дописывает только заданные токены в серверный `.env`.
Без `API_RUNTIME_OVERRIDES` остальные настройки сохраняются; с ним меняются только
явно переданные параметры (см. ниже).
Пустой отдельный Secret не стирает существующий токен из `ENV_PRODUCTION`. Значения
не выводятся в логи. Эти токены не заменяют JWT сотрудников или пароль PostgreSQL.

Само создание Secrets не запускает деплой. Код интеграции поставляется отдельным
[PR #42](https://github.com/team-medovik/hack-autumn-2026-api/pull/42).
Для его включения нужны `APP_ML_ENABLED=true`, `APP_GENERATOR_ENABLED=true`,
`APP_GENERATOR_USE_FOR_DASHBOARD=true`, адреса `http://ml-service:8000` и
`http://generator-api:8000` в сети `backend` и согласованная версия модели.
Существующие значения этих параметров эта настройка токенов не изменяет.
ML workflow использует `ML_SERVICE_TOKEN`; запуск генератора ведётся отдельно и
должен передавать `GENERATOR_SERVICE_TOKEN` из Secrets в окружение generator-api.


### Дополнение настроек API без потери ENV_PRODUCTION

Repository Secret `API_RUNTIME_OVERRIDES` — JSON-объект со строковыми значениями
настроек Spring/PostgreSQL, JWT, OIDC и `APP_ML_*`. Он содержит согласованные
оператором значения; сам JSON с паролями в репозитории не хранится.

Порядок при следующем деплое:

1. Берётся прежний `ENV_PRODUCTION` целиком.
2. Настройки, явно указанные в `API_RUNTIME_OVERRIDES`, перекрывают одноимённые ключи.
   Остальные поля остаются прежними.
3. `APP_ML_SERVICE_TOKEN` и `APP_GENERATOR_SERVICE_TOKEN` берутся из отдельных Secrets.
4. Workflow сохраняет обязательные `SPRING_PROFILES_ACTIVE=prod` и `USERS_ENV_DIR`.

`API_RUNTIME_OVERRIDES` не нужен ML-контейнеру. Там используется отдельный Secret
`ML_SERVICE_TOKEN`; пароль PostgreSQL API, JWT и Keycloak модели не передаются.
Пароль БД в конфигурации не меняет пароль роли на сервере. Создание Secrets не
включает сервисы до деплоя соответствующего кода. Флаги генератора это дополнение
не меняет; они остаются под управлением исходного `ENV_PRODUCTION`.
