param(
    [switch]$AllowLoopback
)
$ErrorActionPreference = 'Stop'
function Invoke-DatabaseQuery {
    param([string]$Query)
    $result = docker compose exec -T postgres psql -U postgres -d postgres -Atc $Query
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL verification failed' }
    return $result
}
$names = @('auth', 'product', 'order', 'payment', 'notification')
foreach ($name in $names) {
    $other = if ($name -eq 'auth') { 'product' } else { 'auth' }
    $query = "SELECT has_database_privilege('${name}_user', '${name}_db', 'CONNECT') AND NOT has_database_privilege('${name}_user', '${other}_db', 'CONNECT')"
    if ((Invoke-DatabaseQuery $query) -ne 't') { throw "Database isolation failed: $name" }
}
$owners = @(Invoke-DatabaseQuery "SELECT datname || ':' || pg_get_userbyid(datdba) FROM pg_database WHERE datname LIKE '%_db' ORDER BY datname")
$expected = @($names | ForEach-Object { "${_}_db:${_}_user" } | Sort-Object)
if (($owners -join ',') -ne ($expected -join ',')) { throw 'Expected five owned databases' }
$queues = docker compose exec -T rabbitmq rabbitmqctl -q list_queues name durable
if ($LASTEXITCODE -ne 0) { throw 'Cannot list RabbitMQ queues' }
foreach ($queue in @('payment.order-created.q', 'order.payment-result.q', 'notification.order.q',
        'payment.order-created.q.dlq', 'order.payment-result.q.dlq', 'notification.order.q.dlq', 'payment.order-expired.q', 'payment.order-expired.q.dlq',
        'notification.fulfillment.q', 'notification.fulfillment.q.dlq', 'payment.cod-lifecycle.q', 'payment.cod-lifecycle.q.dlq',
        'payment.return-received.q', 'payment.return-received.q.dlq', 'order.return-result.q', 'order.return-result.q.dlq')) {
    if (($queues -join "`n") -notmatch ([regex]::Escape($queue) + '\s+true')) {
        throw "Missing durable queue: $queue"
    }
}
$policies = docker compose exec -T rabbitmq rabbitmqctl -q list_policies
if ($LASTEXITCODE -ne 0) { throw 'Cannot list RabbitMQ policies' }
foreach ($queue in @('payment.order-created.q', 'order.payment-result.q', 'notification.order.q', 'payment.order-expired.q', 'notification.fulfillment.q', 'payment.cod-lifecycle.q', 'payment.return-received.q', 'order.return-result.q')) {
    if (($policies -join "`n") -notmatch [regex]::Escape("dlx-$queue")) {
        throw "Missing scoped DLX policy: $queue"
    }
}
$bindings = docker compose exec -T rabbitmq rabbitmqctl -q list_bindings source_name destination_name routing_key
if ($LASTEXITCODE -ne 0) { throw 'Cannot list RabbitMQ bindings' }
foreach ($key in @('order.created', 'payment.succeeded', 'payment.failed', 'order.confirmed', 'order.cancelled',
        'order.expired', 'order.refund-requested', 'order.shipped', 'order.completed', 'order.cod-created', 'order.cod-shipped', 'order.cod-cancelled',
        'order.return-received', 'payment.return-refund-ready', 'payment.return-refunded')) {
    if (($bindings -join "`n") -notmatch [regex]::Escape($key)) { throw "Missing binding: $key" }
}
foreach ($service in @('postgres', 'rabbitmq')) {
    $container = docker compose ps -q $service
    $ports = docker inspect --format '{{json .HostConfig.PortBindings}}' $container | ConvertFrom-Json
    foreach ($property in $ports.PSObject.Properties) {
        foreach ($binding in $property.Value) {
            if (-not $AllowLoopback -or $binding.HostIp -ne '127.0.0.1') {
                throw "Unexpected exposed port for $service"
            }
        }
    }
}
Write-Output 'PASS: five isolated databases/users, durable event topology and private ports.'
