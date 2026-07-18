# Журнал решений Car ADB Gateway

ADR-lite журнал продуктовых и архитектурных решений. Принятые записи не
удаляются: при изменении направления статус меняется на `Superseded` и добавляется
ссылка на новое решение. Нормативный текущий дизайн находится в
[CLOUD-ARCHITECTURE.md](CLOUD-ARCHITECTURE.md).

<a id="cag-001"></a>
## CAG-001 — Только relay, без LAN

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Новое приложение нужно для надёжной удалённой диагностики; LAN-сценарий уже принадлежит Denza Gateway.
- **Решение:** В новом APK нет LAN-listener, LAN-UI и локального подключения.
- **Почему:** Один сетевой путь даёт пользователю одну модель и делает recovery/access-control проверяемыми.
- **Альтернативы:** Hybrid LAN/cloud; расширение существующего приложения.
- **Последствия и риски:** Доступность relay становится зависимостью продукта; локальный аварийный доступ остаётся вне этого APK.
- **Evidence:** `InnerGatewayServer` bind только на `127.0.0.1`; Wi‑Fi permission отсутствует; APK собирается.
- **Пересмотреть, если:** Появится отдельно авторизованный offline-сценарий, который можно явно изолировать от relay.

<a id="cag-002"></a>
## CAG-002 — Отдельное универсальное приложение

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Denza Gateway — брендированный LAN-продукт, а новый механизм применим к любому Android-ГУ с локальным ADB.
- **Решение:** Модуль `:car-adb-gateway`, application ID `ru.adbgw.gateway`; старый APK устанавливается рядом.
- **Почему:** Нет рискованной миграции и Denza-specific наследия в generic-продукте.
- **Альтернативы:** Переименовать/заменить `denza-gateway`; встроить cloud в него.
- **Последствия и риски:** Два gateway APK нужно ясно различать; compatibility shim намеренно отсутствует.
- **Evidence:** Отдельные Gradle module, manifest, package, launcher и `car-adb-gateway.apk`.
- **Пересмотреть, если:** Legacy LAN-приложение будет официально выведено из эксплуатации.

<a id="cag-003"></a>
## CAG-003 — Фиксированный relay и pinning

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Настраиваемый или TOFU relay ослабляет one-code onboarding для нетехнического пользователя.
- **Решение:** Только `adbgw.ru:443` и fingerprint `SHA256:w02E2cvN65HmjeC6h9aLY/6zovde3nvqorQPYtNRp6c` в Android и CLI.
- **Почему:** Скопированный код не может незаметно отправить машину на чужой host.
- **Альтернативы:** Self-hosted адрес в UI; TOFU; host certificate CA.
- **Последствия и риски:** Ротация ключа/переезд требуют согласованного обновления. Настройка self-hosted из v2 superseded.
- **Evidence:** Константы Android/Go; Ansible verify проверяет DNS и fingerprint.
- **Пересмотреть, если:** Появится подписанный key-rotation или отдельная enterprise-сборка.

<a id="cag-004"></a>
## CAG-004 — Один компьютер

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Пользователю автомобиля не нужен менеджер ключей разработчиков.
- **Решение:** Один trusted computer и одна активная inner SSH-сессия на машину.
- **Почему:** «Подключить/заменить компьютер» понятно и однозначно отзывает доступ.
- **Альтернативы:** Список компьютеров; несколько разработчиков одновременно; только admin grants.
- **Последствия и риски:** Смена разработчика требует нового кода; одновременная работа исключена.
- **Evidence:** Один active grant на device; inner server хранит один ключ и закрывает старые сессии.
- **Пересмотреть, если:** Подтверждённая потребность в concurrency важнее UX/security стоимости.

<a id="cag-005"></a>
## CAG-005 — Двухфазная замена

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Отзыв старого ключа до проверки нового может оставить машину без доступа.
- **Решение:** Новый ключ сначала pending; commit только после inner auth; expiry не меняет active.
- **Почему:** Неуспешная замена неразрушительна, операции идемпотентны.
- **Альтернативы:** Revoke-then-add; бессрочно два ключа; замена только на relay.
- **Последствия и риски:** В коротком pending-окне оба ключа достигают порта, но внутри работает старый ключ или правильный код.
- **Evidence:** Relay replacement/expiry unit tests и CLI failed-replacement test.
- **Пересмотреть, если:** Изменится pairing protocol или state переедет во внешнюю транзакционную систему.

<a id="cag-006"></a>
## CAG-006 — One-code bootstrap

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Сверка fingerprint или второй secret делают pairing ошибкоопасным.
- **Решение:** Один код stages developer key и авторизует его на машине; fixed relay даёт первый inner host key, затем CLI пинит его.
- **Почему:** Целевой UX — одна команда `cag pair CODE` без знания SSH.
- **Альтернативы:** Out-of-band fingerprint; два кода; QR mutual auth; admin bundle.
- **Последствия и риски:** Relay, скомпрометированный при bootstrap, может подменить inner key; позже mismatch fail closed.
- **Evidence:** CLI использует временный candidate known-hosts и сохраняет vehicle key только после `pair-complete`.
- **Пересмотреть, если:** Появится QR-камера или hardware-backed vehicle identity.

<a id="cag-007"></a>
## CAG-007 — Штатная ADB-авторизация

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Обычный APK не может молча авторизовать собственный ADB key.
- **Решение:** App-private ADB RSA key и стандартный Android authorization dialog — обязательная часть onboarding.
- **Почему:** Используется реальная platform trust boundary без root/system APK.
- **Альтернативы:** Предустановленные keys; system signing; reuse developer key; только smart socket.
- **Последствия и риски:** Один раз нужен человек у ГУ; raw desktop ADB может запросить второе подтверждение.
- **Evidence:** `AdbProvisioner`/`LocalAdbClient`, отдельное состояние `AuthorizationRequired`.
- **Пересмотреть, если:** Firmware даст документированный privileged enrollment API.

<a id="cag-008"></a>
## CAG-008 — Активность без разбора команд

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Точные shell-команды требуют парсинга ADB и создают privacy/maintenance риск.
- **Решение:** Только SSH session/channel events, duration, pulse и last activity.
- **Почему:** Эти сигналы надёжны и дают «жизнь» большому экрану.
- **Альтернативы:** Парсить ADB shell; raw log; совсем не показывать активность.
- **Последствия и риски:** Не видно конкретную команду; «работает» означает открытый ADB channel, а не постоянный трафик.
- **Evidence:** Listeners в `InnerGatewayServer`, `StatusCard`; payload logger отсутствует.
- **Пересмотреть, если:** Появится privacy-reviewed требование command audit.

<a id="cag-009"></a>
## CAG-009 — Persistent manual disconnect

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Пользователь должен остановить доступ, не борясь с self-healing.
- **Решение:** Закрыть tunnel/sessions, сохранить disabled и не reconnect после boot до ручного enable.
- **Почему:** Явное намерение пользователя важнее автоматического восстановления.
- **Альтернативы:** Временный disconnect; только stop service; admin revoke.
- **Последствия и риски:** Enable требует relay; non-forwarding control key остаётся для этого действия.
- **Evidence:** Reducer test, state store, boot guard и relay disable test.
- **Пересмотреть, если:** Потребуются timed/admin-enforced окна отключения.

<a id="cag-010"></a>
## CAG-010 — Платформы и распространение

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Есть Android 8 ГУ, APK ставится с USB, разработчики используют разные desktop OS.
- **Решение:** minSdk 26, APK distribution, один Go CLI для macOS/Linux; Windows не входит в первый release.
- **Почему:** Покрывается целевое ГУ без privileged install, а CLI не зависит от macOS-only tools.
- **Альтернативы:** Play Store; Android 12+; shell script; Windows в MVP.
- **Последствия и риски:** Обновление APK ручное; OEM background behavior требует живых тестов.
- **Evidence:** Gradle assembly, Darwin build и Linux cross-build.
- **Пересмотреть, если:** Появится managed update channel или спрос на Windows.

<a id="cag-011"></a>
## CAG-011 — `specialUse` foreground service

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Android 15 ограничивает длительность/boot для `dataSync`; tunnel долгоживущий и user-visible.
- **Решение:** `specialUse` с subtype, `START_STICKY`, boot/package receivers, без постоянного WakeLock.
- **Почему:** Тип соответствует функции и не злоупотребляет time-limited service.
- **Альтернативы:** `dataSync`; без FGS; WakeLock; WorkManager polling.
- **Последствия и риски:** OEM всё ещё может ограничить приложение; store review потребует отдельной проверки.
- **Evidence:** Manifest и `GatewayService`; dataSync/WakeLock permissions отсутствуют.
- **Пересмотреть, если:** Android введёт dedicated remote-diagnostics type или появится privileged daemon.

<a id="cag-012"></a>
## CAG-012 — Границы self-healing

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Сеть/relay/sleep/adbd ломаются штатно, но identity/key ошибки нельзя retry бесконечно.
- **Решение:** Независимо supervise ADB/inner SSH и relay; transient — bounded jitter retry; mismatch/revoked/corrupt — fail closed. Force Stop требует ручного запуска.
- **Почему:** Здоровые компоненты остаются подняты, security failures видимы.
- **Альтернативы:** Рестартовать всё; uniform infinite retry; WakeLock; privileged watchdog.
- **Последствия и риски:** Обычный APK не лечит Force Stop; OEM killers требуют evidence.
- **Evidence:** Supervisor/backoff tests, heartbeat, network callback, boot receiver и support-текст.
- **Пересмотреть, если:** APK станет system/privileged или soak найдёт новый failure class.

<a id="cag-013"></a>
## CAG-013 — Два предупреждения о полном доступе

- **Статус / дата:** Accepted — 2026-07-18.
- **Контекст:** Enrollment создаёт remote capability, pairing-код делегирует её компьютеру.
- **Решение:** Предупреждать до enrollment и отдельно перед каждым pairing-кодом; указать trusted source/person.
- **Почему:** Это разные рискованные действия с разными участниками.
- **Альтернативы:** Один onboarding disclaimer; footnote; без повторного warning.
- **Последствия и риски:** Один дополнительный tap в редких high-impact сценариях.
- **Evidence:** Два независимых AlertDialog flow в `MainActivity`.
- **Пересмотреть, если:** Managed policy обеспечит эквивалентное informed consent вне APK.
