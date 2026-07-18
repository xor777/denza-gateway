# Car ADB Gateway — relay-only архитектура v3

Статус: нормативное описание реализации в этом репозитории. Обновлено
2026-07-18. Эта версия relay ещё не развёрнута; живые E2E- и soak-проверки
остаются обязательными перед production.

Car ADB Gateway — отдельное универсальное Android-приложение для автомобильных
ГУ, где ADB доступен локально. Оно устанавливается рядом с существующим LAN-only
Denza Gateway и не зависит от марки автомобиля.

В v3 нет LAN-листенера, LAN-сценария и настройки собственного relay
([CAG-001](CAR-ADB-GATEWAY-DECISIONS.md#cag-001),
[CAG-002](CAR-ADB-GATEWAY-DECISIONS.md#cag-002),
[CAG-003](CAR-ADB-GATEWAY-DECISIONS.md#cag-003)). Обоснования и история
пересмотра находятся в [журнале решений](CAR-ADB-GATEWAY-DECISIONS.md); этот
документ задаёт текущее поведение.

## 1. Роли и выдача доступа

Публичная доступность TCP-порта 443 сама по себе не даёт ни одной роли доступ к
автомобилю.

| Роль | Как получает полномочие | Что может делать |
| --- | --- | --- |
| Автомобиль | Администратор relay создаёт enrollment-код на 60 минут. APK создаёт device-key и один раз использует код. | Публиковать только свой loopback-порт; создавать pairing-код; подтверждать замену или отключать свой grant. |
| Администратор relay | Обычный административный SSH-ключ, порт 22. | Развернуть/проверить host, создать приглашение автомобилю, проверить или отозвать серверное состояние. Для каждого разработчика не нужен. |
| Разработчик | Человек у экрана зарегистрированного автомобиля подтверждает предупреждение и показывает pairing-код на 10 минут. `cag pair CODE` регистрирует public key этого компьютера для этой машины. | Открыть только назначенный порт этой машины и пройти end-to-end аутентификацию на ней. Shell relay и другие машины недоступны. |

APK без enrollment-кода не может зарегистрироваться на relay. Pairing-код с
экрана уже зарегистрированной машины не подходит для регистрации нового авто.

## 2. Топология и границы доверия

```text
Android ГУ                              relay: adbgw.ru                 компьютер разработчика

ADB 5037/5555 <- sshd 127.0.0.1:2222 <- SSH -R -> 127.0.0.1:device-port <- SSH -W -> cag CLI
                  end-to-end SSH             OpenSSH/PAM, порт 443       macOS или Linux
```

- Автомобиль всегда инициирует соединение наружу.
- Единственный Android-листенер — `127.0.0.1:2222`; Wi‑Fi, cellular и wildcard
  интерфейсы не слушаются.
- Порты машин на VPS также доступны только через loopback relay.
- `adbgw.ru:443` и Ed25519 fingerprint зашиты в Android и CLI. Mismatch —
  постоянная безопасная ошибка, а не повод бесконечно повторять подключение
  ([CAG-003](CAR-ADB-GATEWAY-DECISIONS.md#cag-003)).
- Relay видит outer identity и соответствие ключа порту. Внутренний SSH между
  компьютером и машиной зашифрован end-to-end.
- Во время первого one-code bootstrap фиксированный relay считается доверенным
  источником inner host key; затем CLI пинит этот ключ
  ([CAG-006](CAR-ADB-GATEWAY-DECISIONS.md#cag-006)).

## 3. Relay

`ops/ansible` настраивает stock OpenSSH и команды из `relay/`. Порт 22 остаётся
административным; порт 443 обслуживает приложение.

### 3.1 Ограниченные аккаунты

| Аккаунт | Аутентификация | Ограничение |
| --- | --- | --- |
| `cag-device` | Отдельный public key машины | Только remote forwarding и `permitlisten=127.0.0.1:<assigned-port>`; команд нет. |
| `cag-client` | Активный или pending public key разработчика | Только local forwarding и `permitopen=127.0.0.1:<assigned-port>`; команд нет. |
| `cag-enroll` | Временный admin invite через PAM | Forwarding запрещён; только forced enrollment command. |
| `cag-pair` | Pairing-код машины через PAM | Forwarding запрещён; только forced staging command. |
| `cag-control` | Public key машины | Forwarding запрещён; per-key forced command ограничен device ID. |
| `cag-authkeys` | SSH login отсутствует | Читает state для `AuthorizedKeysCommand`. |

TTY, shell, X11, agent forwarding, tunnel device, user RC и Unix socket
forwarding запрещены. Control-key остаётся зарегистрированным при выключенном
доступе, чтобы пользователь мог вручную включить его снова; этот ключ не умеет
форвардить трафик.

### 3.2 State, коды и rate limit

`/opt/cag/state/state.json` — единственный изменяемый state. Каждая операция,
способная удалить просроченную запись или изменить данные, держит
`/opt/cag/state/locks/state.lock` через `flock`; запись использует fsync и
атомарный rename.

State содержит машины, public keys клиентов, активные grants, pending-замены,
коды и per-source rate limit. `AuthorizedKeysCommand` динамически строит из него
строки с ограничениями.

- Enrollment: `XXXX-XXXX`, TTL по умолчанию 60 минут, одна машина. Повтор тем же
  device-key после потерянного ответа идемпотентен; другой device-key отклоняется.
- Pairing: `XXXX-XXXX`, TTL 10 минут. После пяти неверных SSH-паролей источник
  блокируется на пять минут.
- Алфавит исключает `0/O` и `1/I`.
- Просроченный pending удаляется без изменения активного grant.

Администратор создаёт первое приглашение так:

```bash
sudo cag-admin invite
```

### 3.3 Двухфазная замена компьютера

У машины один trusted computer и одна активная inner SSH-сессия
([CAG-004](CAR-ADB-GATEWAY-DECISIONS.md#cag-004)). Замена не ломает старый доступ
до подтверждения нового ([CAG-005](CAR-ADB-GATEWAY-DECISIONS.md#cag-005)):

1. Авторизованный автомобиль создаёт pairing request и показывает код.
2. `cag pair CODE` отправляет новый public key. Relay сохраняет его как pending и
   временно пропускает active и pending keys только к порту этой машины.
3. CLI начинает end-to-end SSH к автомобилю, предлагает public key, затем тот же
   код.
4. Автомобиль проверяет код и ключ и выполняет авторизованный идемпотентный
   `pair-commit <fingerprint>`.
5. Relay атомарно заменяет активный grant.
6. Автомобиль сохраняет новый trusted key и закрывает все предыдущие inner
   сессии. Существующий старый outer-forward больше не пройдёт inner auth.

Ошибка или expiry до commit оставляет старые grant и сессию. Повтор enrollment,
pair submission или commit после потерянного ответа безопасен.

## 4. Android-приложение

Gradle-модуль: `car-adb-gateway/`; application ID: `ru.adbgw.gateway`; minSdk 26.
ADB-, device- и inner host keys хранятся в app-private storage, backup приложения
отключён ([CAG-010](CAR-ADB-GATEWAY-DECISIONS.md#cag-010)).

### 4.1 Первая настройка

1. Приложение создаёт собственный ADB RSA key при попытке выполнить локальный
   shell.
2. Если raw adbd требует авторизацию, Android показывает штатный системный
   диалог. Пользователь подтверждает его и нажимает «Проверить снова»
   ([CAG-007](CAR-ADB-GATEWAY-DECISIONS.md#cag-007)).
3. Приложение проверяет shell и best-effort применяет доступные device-idle и
   background app-op настройки через уже авторизованный локальный ADB.
4. Пользователь вводит восьмисимвольный enrollment-код администратора.
5. Перед enrollment появляется предупреждение о полном удалённом доступе.
6. APK регистрирует device/host public keys, сохраняет device ID и порт и
   автоматически включает relay-only доступ.

### 4.2 ADB endpoint

Порядок обнаружения: smart socket `5037`, затем raw adbd `5555`; сначала
loopback, затем каждый собственный IPv4 ГУ. Тип и фактический host отправляются
на relay и входят в pairing bundle. Inner SSH разрешает forwarding только к
точно обнаруженным host и port.

При raw `5555` стандартный ADB key компьютера может вызвать ещё один штатный
Android-диалог. Это ожидаемое поведение.

### 4.3 Состояние и видимая активность

Onboarding, ADB, relay, client, enabled-state, pairing и permanent failure —
разные измерения state machine. Главный экран переводит их в простые статусы и
не разбирает/не записывает ADB-команды
([CAG-008](CAR-ADB-GATEWAY-DECISIONS.md#cag-008)).

Активность строится по надёжным SSH-событиям:

- ожидание компьютера;
- компьютер подключён и длительность сессии;
- «удалённый компьютер работает» с пульсацией, пока открыт хотя бы один ADB
  forwarding channel;
- компьютер подключён, но ожидает;
- время последней активности.

Endpoint, relay state, device ID и ограниченный in-memory журнал скрыты в
«Поддержке».

### 4.4 Фоновая работа и восстановление

Foreground service использует `specialUse`, `START_STICKY`, boot/package-update
receivers и default network callback. `dataSync` и постоянный WakeLock не
используются ([CAG-011](CAR-ADB-GATEWAY-DECISIONS.md#cag-011)).

ADB/inner SSH и relay tunnel контролируются независимо:

- ADB проверяется каждые 30 секунд, включая restart adbd и смену endpoint.
  Авторизация перепроверяется, а фоновые app-ops не меняются повторно при
  стабильном endpoint.
- Relay reconnect использует jittered exponential backoff 1–60 секунд. Каждая
  попытка подключается по hostname и заново проходит DNS resolution.
- Смена сети закрывает старый tunnel и сразу сбрасывает backoff.
- SSH heartbeat — 30 секунд, максимум три ответа могут быть пропущены.
- Watchdog восстанавливает отсутствующий loopback server, не перезапуская
  здоровые компоненты.
- DNS, отсутствие сети, restart relay, sleep/wake и временная потеря ADB —
  восстанавливаемые ошибки. Host-key mismatch, отозванный device-key и
  повреждённые ключи останавливают auto-retry в безопасном видимом состоянии.

Force Stop — системная граница Android: обычный APK не может запустить себя после
него до ручного открытия ([CAG-012](CAR-ADB-GATEWAY-DECISIONS.md#cag-012)).

### 4.5 Ручное отключение

«Отключить удалённый доступ» закрывает inner sessions и tunnel, сохраняет
`enabled=false` и best-effort выключает forwarding на relay. После reboot сервис
не стартует. Обратное включение — только явное действие пользователя и требует
успешного короткого control-соединения
([CAG-009](CAR-ADB-GATEWAY-DECISIONS.md#cag-009)).

## 5. Интерфейс

Приложение landscape-first для экрана 15–16″. Основной экран содержит большую
status-карточку, имя автомобиля, живую активность, одно действие
«Подключить/Заменить компьютер» и «Отключить удалённый доступ». SSH, PAM,
forwarding, smart socket и fingerprints отсутствуют вне поддержки.

Два отдельных предупреждения о полном доступе
([CAG-013](CAR-ADB-GATEWAY-DECISIONS.md#cag-013)) показываются:

1. перед первым enrollment автомобиля;
2. перед каждым показом pairing-кода разработчику.

Pairing-экран говорит, что код можно передавать только доверенному человеку, и
показывает `cag pair XXXX-XXXX`. Списка компьютеров нет: пользователь мыслит
одним текущим компьютером и его заменой.

## 6. CLI разработчика

`cli/` собирает один Go-бинарник для macOS и Linux и не меняет
`~/.ssh/config`. Runtime-зависимости: системный OpenSSH и Android Platform Tools
при выполнении ADB-команды.

```text
cag pair <CODE>
cag connect
cag connect -- adb ...
cag status
cag disconnect
```

State лежит в системной user config directory: `~/.config/cag` в Linux/XDG и
`~/Library/Application Support/cag` в macOS. Там находятся client key, fixed
relay host key, pinned vehicle host key, active bundle и SSH control socket.

`cag connect` оставляет ControlMaster tunnel в фоне с keepalive 30 секунд. Для
smart socket используется `ADB_SERVER_SOCKET`; для raw adbd — свободный local
port и `adb connect`.

## 7. Остаточные риски

- Любой может выполнить публичный TCP/SSH handshake на 443, но shell/forwarding
  недоступны без временного кода или зарегистрированного private key.
- Developer key ограничен loopback-портом одной машины; device-key — публикацией
  своего порта; control-key не умеет форвардить.
- Relay доверен во время первого one-code bootstrap. Скомпрометированный в этот
  момент relay способен подменить inner host key. Это принятый UX-компромисс
  CAG-006; последующие изменения ключа fail closed.
- Утёкший действующий pairing-код достаточен для замены trusted computer. Риск
  ограничивают предупреждение, TTL 10 минут, лимит попыток и модель одной машины.
- Uninstall APK удаляет private keys и state. Новая установка требует новый
  enrollment invite администратора.

## 8. Verification status

Локально выполнено 2026-07-18:

- relay unit: TTL, source lockout, идемпотентный enrollment, pending rollback,
  двухфазная замена и persistent disable;
- Ansible syntax check и production-profile lint;
- CLI unit, `go vet`, Darwin build и Linux cross-build;
- Android unit: state reducer, backoff, endpoint plan и relay protocol;
- сборка debug APK с minSdk 26 и targetSdk 36, Android lint без ошибок.

До production обязательны:

- deploy новой Ansible-роли и негативные SSH integration tests на relay;
- полный relay → CLI → inner SSH → реальный ADB E2E;
- API 26, 31, 34, 35 и 36;
- живое ГУ: установка/ADB approval, reboot, sleep/wake, network switching, adbd и
  relay restart, замена Mac/Linux-компьютера;
- socket inspection на ГУ, подтверждающий отсутствие внешнего listener;
- 72-часовой fault-injection soak и измерение восстановления: relay/network не
  более 90 секунд, ADB не более 60 секунд.
