# Relay Server Ansible

Ansible configuration for the Car ADB Gateway VPS.

## Local Setup

```bash
cd ops/ansible
python3 -m venv .venv-ansible
.venv-ansible/bin/pip install -r requirements.txt
.venv-ansible/bin/ansible-galaxy collection install -r requirements.yml
```

## Configure the Server

```bash
.venv-ansible/bin/ansible-playbook playbooks/relay-host.yml
.venv-ansible/bin/ansible-playbook playbooks/verify-relay-host.yml
```

Repeated runs are safe and converge the server to the configuration in this
repository.

After configuration, create a single-use invite code. Its default lifetime is
60 minutes:

```bash
ssh adbgw.ru sudo cag-admin invite
```

Enrollment, pairing, and grant state is stored atomically in
`/opt/cag/state/state.json` under a shared lock. Codes are time-limited, and five
invalid attempts from one source trigger a five-minute lockout.

## Provision a New VPS

```bash
.venv-ansible/bin/ansible-playbook -u root --ask-pass playbooks/bootstrap.yml
ssh dmitry@95.179.132.238 'sudo -n true'
.venv-ansible/bin/ansible-playbook \
  -e confirm_ssh_lockdown=true \
  playbooks/lockdown.yml
```

If package updates require a reboot:

```bash
.venv-ansible/bin/ansible-playbook -u root --ask-pass \
  -e confirm_reboot=true \
  playbooks/reboot.yml
```

Never store passwords or private keys in inventory or Ansible variables.

## DNS

```text
A  @  95.179.132.238
```

An `AAAA` record is not required yet. Port 443 carries SSH, not HTTPS. The
verification playbook requires the domain to resolve to this IPv4 address and
the Ed25519 host-key fingerprint to match the pinned value.
