"""Verify five isolated databases and pre-provisioned durable event topology."""
import json
import subprocess

compose = ['docker', 'compose']

def execute(*arguments):
    return subprocess.check_output([*compose, *arguments], text=True).strip()

databases = execute('exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'postgres', '-Atc',
    "SELECT datname || ':' || pg_get_userbyid(datdba) FROM pg_database WHERE datname LIKE '%_db' ORDER BY datname")
expected = sorted(f'{name}_db:{name}_user' for name in (
    'auth', 'product', 'order', 'payment', 'notification'))
assert databases.splitlines() == expected, databases
for name in ('auth', 'product', 'order', 'payment', 'notification'):
    isolated = execute('exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'postgres', '-Atc',
        f"SELECT has_database_privilege('{name}_user', '{name}_db', 'CONNECT') "
        f"AND NOT has_database_privilege('{name}_user', '"
        f"{'product' if name == 'auth' else 'auth'}_db', 'CONNECT')")
    assert isolated == 't', (name, isolated)
queues = execute('exec', '-T', 'rabbitmq', 'rabbitmqctl', '-q', 'list_queues', 'name', 'durable')
for name in ('payment.order-created.q', 'order.payment-result.q', 'notification.order.q'):
    assert f'{name}\ttrue' in queues, queues
bindings = execute('exec', '-T', 'rabbitmq', 'rabbitmqctl', '-q', 'list_bindings',
    'source_name', 'destination_name', 'routing_key')
for key in ('order.created', 'payment.succeeded', 'payment.failed', 'order.confirmed', 'order.cancelled'):
    assert key in bindings, bindings
for service in ('postgres', 'rabbitmq'):
    container = execute('ps', '-q', service)
    ports = json.loads(subprocess.check_output([
        'docker', 'inspect', '--format', '{{json .HostConfig.PortBindings}}', container], text=True))
    assert not ports, (service, ports)
print('PASS: 5 databases/users isolated; 3 durable queues and 5 event bindings; DB/MQ ports private.')
