# Server Ansible

Ansible-конфигурация VPS для Car ADB Gateway.

## Локальная установка

```bash
cd ops/ansible
python3 -m venv .venv-ansible
.venv-ansible/bin/pip install -r requirements.txt
.venv-ansible/bin/ansible-galaxy collection install -r requirements.yml
```

## Настройка сервера

```bash
.venv-ansible/bin/ansible-playbook playbooks/relay-host.yml
.venv-ansible/bin/ansible-playbook playbooks/verify-relay-host.yml
```

Повторный запуск безопасен и приводит сервер к конфигурации из репозитория.

После конфигурации создайте одноразовый invite-код (по умолчанию на 60 минут):

```bash
ssh adbgw.ru sudo cag-admin invite
```

Состояние enrollment, pairing и grant хранится атомарно в
`/opt/cag/state/state.json` под общей блокировкой. Коды ограничены по времени,
а пять неверных попыток с одного адреса включают пятиминутную паузу.

## Развёртывание нового VPS

```bash
.venv-ansible/bin/ansible-playbook -u root --ask-pass playbooks/bootstrap.yml
ssh dmitry@95.179.132.238 'sudo -n true'
.venv-ansible/bin/ansible-playbook \
  -e confirm_ssh_lockdown=true \
  playbooks/lockdown.yml
```

Если после обновлений требуется перезагрузка:

```bash
.venv-ansible/bin/ansible-playbook -u root --ask-pass \
  -e confirm_reboot=true \
  playbooks/reboot.yml
```

Пароли и приватные ключи нельзя хранить в inventory или переменных Ansible.

## DNS

```text
A  @  95.179.132.238
```

Запись `AAAA` пока не нужна. Порт 443 используется для SSH, а не HTTPS.
Проверочный playbook требует, чтобы домен указывал на этот IPv4 и fingerprint
Ed25519 host key совпадал с закреплённым значением.
