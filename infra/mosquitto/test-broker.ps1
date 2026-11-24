[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$envFile = Join-Path $repositoryRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Root .env is missing. Create it from .env.example and set the MQTT passwords.'
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([^#=]+?)\s*=(.*)$') {
        $values[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$devices = @(
    @{ Username = 'device-ss-demo-9101'; MachineId = '9101'; PasswordKey = 'MQTT_DEVICE_9101_PASSWORD' }
)
$servicePassword = $values['MQTT_MACHINE_SERVICE_PASSWORD']
$passwords = @($servicePassword)
foreach ($device in $devices) {
    $password = $values[$device.PasswordKey]
    if (-not $password -or $password.StartsWith('replace-with-') -or $password.Length -lt 16) {
        throw "Set a unique password of at least 16 characters for $($device.Username) in .env."
    }
    $passwords += $password
}
if (-not $servicePassword -or $servicePassword.StartsWith('replace-with-') -or
        $servicePassword.Length -lt 16) {
    throw 'Set a unique password of at least 16 characters for Machine Service in .env.'
}
if ($servicePassword -eq $values['MQTT_DEVICE_9101_PASSWORD']) {
    throw 'Machine Service and each demo device must have different MQTT passwords.'
}

$brokerId = (docker compose ps -q mqtt-broker).Trim()
if (-not $brokerId) {
    throw 'mqtt-broker is not running. Initialize credentials and start the broker first.'
}

$source = $devices[0]
$targetMachineId = '9102'
$nonce = [Guid]::NewGuid().ToString('N')
$tokens = @{
    '__SOURCE_USERNAME__' = $source.Username
    '__SOURCE_PASSWORD__' = $values[$source.PasswordKey]
    '__SOURCE_MACHINE_ID__' = $source.MachineId
    '__TARGET_MACHINE_ID__' = $targetMachineId
    '__SERVICE_PASSWORD__' = $servicePassword
    '__NONCE__' = $nonce
}
$containerScript = @'
set -eu
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT

mosquitto_sub -V mqttv311 -h 127.0.0.1 -p 1883 -C 1 -W 5 \
  -u '__SOURCE_USERNAME__' -P '__SOURCE_PASSWORD__' \
  -t 'scentstation/__SOURCE_MACHINE_ID__/command' >"$test_dir/command" &
subscriber_pid=$!
sleep 1
mosquitto_pub -V mqttv311 -h 127.0.0.1 -p 1883 -q 1 \
  -u machine-service -P '__SERVICE_PASSWORD__' \
  -t 'scentstation/__SOURCE_MACHINE_ID__/command' -m 'command-__NONCE__'
wait "$subscriber_pid"
test "$(cat "$test_dir/command")" = 'command-__NONCE__'
echo 'PASS: Machine Service command reached the intended device over QoS 1.'

mosquitto_sub -V mqttv311 -h 127.0.0.1 -p 1883 -C 1 -W 5 \
  -u machine-service -P '__SERVICE_PASSWORD__' \
  -t 'scentstation/__SOURCE_MACHINE_ID__/response' >"$test_dir/response" &
subscriber_pid=$!
sleep 1
mosquitto_pub -V mqttv311 -h 127.0.0.1 -p 1883 -q 1 \
  -u '__SOURCE_USERNAME__' -P '__SOURCE_PASSWORD__' \
  -t 'scentstation/__SOURCE_MACHINE_ID__/response' -m 'response-__NONCE__'
wait "$subscriber_pid"
test "$(cat "$test_dir/response")" = 'response-__NONCE__'
echo 'PASS: Device response reached Machine Service over QoS 1.'

mosquitto_sub -V mqttv311 -h 127.0.0.1 -p 1883 -C 1 -W 3 \
  -u '__SOURCE_USERNAME__' -P '__SOURCE_PASSWORD__' \
  -t 'scentstation/__TARGET_MACHINE_ID__/command' \
  >"$test_dir/wrong-command" 2>"$test_dir/wrong-command.err" &
subscriber_pid=$!
sleep 1
mosquitto_pub -V mqttv311 -h 127.0.0.1 -p 1883 -q 1 \
  -u machine-service -P '__SERVICE_PASSWORD__' \
  -t 'scentstation/__TARGET_MACHINE_ID__/command' -m 'other-command-__NONCE__'
if wait "$subscriber_pid"; then
  if grep -Fq 'other-command-__NONCE__' "$test_dir/wrong-command"; then
    echo 'ACL failure: device received a command for another machine.' >&2
    exit 1
  fi
else
  test ! -s "$test_dir/wrong-command"
fi
echo 'PASS: Device cannot receive another machine command.'

mosquitto_sub -V mqttv311 -h 127.0.0.1 -p 1883 -C 1 -W 3 \
  -u machine-service -P '__SERVICE_PASSWORD__' \
  -t 'scentstation/__TARGET_MACHINE_ID__/response' \
  >"$test_dir/cross-response" 2>"$test_dir/cross-response.err" &
subscriber_pid=$!
sleep 1
mosquitto_pub -V mqttv311 -h 127.0.0.1 -p 1883 -q 1 \
  -u '__SOURCE_USERNAME__' -P '__SOURCE_PASSWORD__' \
  -t 'scentstation/__TARGET_MACHINE_ID__/response' -m 'cross-__NONCE__'
if wait "$subscriber_pid"; then
  if grep -Fq 'cross-__NONCE__' "$test_dir/cross-response"; then
    echo 'ACL failure: device published to another machine response.' >&2
    exit 1
  fi
else
  test ! -s "$test_dir/cross-response"
fi
echo 'PASS: Cross-machine device response was not delivered.'

if mosquitto_pub -V mqttv311 -h 127.0.0.1 -p 1883 -q 1 \
  -u '__SOURCE_USERNAME__' -P "invalid-__NONCE__" \
  -t 'scentstation/__SOURCE_MACHINE_ID__/response' -m 'invalid-auth' \
  2>"$test_dir/invalid-auth.err"; then
  echo 'Authentication failure: invalid password was accepted.' >&2
  exit 1
fi
echo 'PASS: Invalid device password is rejected.'

if mosquitto_pub -V mqttv311 -h 127.0.0.1 -p 1883 -q 1 -r \
  -u '__SOURCE_USERNAME__' -P '__SOURCE_PASSWORD__' \
  -t 'scentstation/__SOURCE_MACHINE_ID__/response' -m 'retained' \
  2>"$test_dir/retained.err"; then
  echo 'Retained message was accepted.' >&2
  exit 1
fi
echo 'PASS: Retained messages are rejected.'
'@
foreach ($token in $tokens.GetEnumerator()) {
    $containerScript = $containerScript.Replace($token.Key, $token.Value)
}

Push-Location $repositoryRoot
try {
    $normalizedScript = $containerScript.Replace("`r`n", "`n").Replace("`r", "`n")
    $encodedScript = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($normalizedScript))
    $command = "printf '%s' '$encodedScript' | base64 -d | sh -e"
    & docker compose exec -T mqtt-broker sh -c $command
    if ($LASTEXITCODE -ne 0) {
        throw 'MQTT protocol/ACL test failed.'
    }
}
finally {
    Pop-Location
}
