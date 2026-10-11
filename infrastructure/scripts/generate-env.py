"""Generate fresh local/VPS secrets without printing or overwriting them."""
import base64
import os
from pathlib import Path
import secrets
import sys

target = Path(sys.argv[1] if len(sys.argv) > 1 else '.env')
values = {
    'POSTGRES_USER': 'postgres',
    **{name: secrets.token_hex(32) for name in (
        'POSTGRES_PASSWORD',
        'AUTH_DB_PASSWORD',
        'PRODUCT_DB_PASSWORD',
        'ORDER_DB_PASSWORD',
        'PAYMENT_DB_PASSWORD',
        'NOTIFICATION_DB_PASSWORD',
        'RABBITMQ_DEFAULT_PASS',
    )},
    'RABBITMQ_DEFAULT_USER': 'commerce_app',
    'JWT_SECRET': base64.b64encode(secrets.token_bytes(32)).decode(),
    'DOMAIN': 'md05-rikkei-project.duckdns.org',
    'AUTH_IMAGE': 'ghcr.io/dth251/md05-rikkei-project/auth-service',
}
with target.open('x', encoding='utf-8', newline='\n') as stream:
    stream.write(''.join(f'{key}={value}\n' for key, value in values.items()))
if os.name != 'nt':
    target.chmod(0o600)
print(f'Created {target}; values are private and must not be committed.')
