#!/usr/bin/env bash
# End-to-end test: starts a master with the RestModule and a wrapper from the jars in */build/libs, creates a
# Paper lobby, a Minestom server from a custom jar and a Velocity proxy through the REST API and checks that they
# come online (the proxy with the MOTD from the API), restart and are removed with their groups.
#
#   ./gradlew build && e2e/run.sh
#
# Needs Java 25, curl, jq, python3 and a MySQL database the user may create tables in. Settings (environment):
#   E2E_PROXY        velocity (default) or bungeecord, the proxy software that is tested
#   E2E_DETACHED     1: servers run in tmux sessions (detachedServers) and the wrapper is restarted while they run
#   E2E_DB_TYPE      mysql (default) or h2, the embedded database (then no MySQL is needed)
#   E2E_DB_HOST/E2E_DB_PORT/E2E_DB_USER/E2E_DB_PASSWORD/E2E_DB_NAME   default localhost/3306/root/root/rexlcloud_e2e
#   E2E_API_PORT, E2E_MASTER_PORT, E2E_WRAPPER_PORT                     default 14343, 11234, 12243
#   E2E_SERVER_JAR   id of the server jar for the lobby (GET /server-jars), default the first Paper jar
#   E2E_JAVA_COMMAND Java of the lobby group, e.g. /opt/jdk-21/bin/java for Paper 1.8 (must be a runtime the wrapper found)
#   E2E_DIR          working folder, default a new temporary one; kept after a failure for the logs
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=$(sed -n "s/^version = '\(.*\)'/\1/p" "$ROOT/build.gradle")
WORK=${E2E_DIR:-$(mktemp -d)}
API_PORT=${E2E_API_PORT:-14343}
MASTER_PORT=${E2E_MASTER_PORT:-11234}
WRAPPER_PORT=${E2E_WRAPPER_PORT:-12243}
API="http://127.0.0.1:$API_PORT/api/v2"
GROUP=E2ELobby
PIDS=()

log() { echo "[e2e] $*"; }

fail() {
  echo "[e2e] FAILED: $*" >&2
  for file in "$WORK/master.log" "$WORK/wrapper.log"; do
    echo "----- last lines of $file -----" >&2
    # Without the generated admin password, which the master prints on its screen (here: the log)
    tail -n 80 "$file" | grep -v "with password" >&2 || true
  done
  # Paper/Velocity log to logs/latest.log, BungeeCord to proxy.log.0
  for file in "$WORK"/wrapper/servers/temp/*/logs/latest.log "$WORK"/wrapper/servers/temp/*/proxy.log.0; do
    [ -f "$file" ] && { echo "----- $file -----" >&2; tail -n 40 "$file" >&2; }
  done
  # The master keeps the consoles of the servers, also of ones whose folder is gone
  if [ -n "${TOKEN:-}" ]; then
    for server in $(curl -s -H "Authorization: Bearer $TOKEN" "$API/servers" | jq -r '.[].server_name' 2>/dev/null); do
      echo "----- console of $server -----" >&2
      curl -s -H "Authorization: Bearer $TOKEN" "$API/servers/$server/console" | jq -r '.lines[-40:][]' 2>/dev/null >&2
    done
  fi
  exit 1
}

cleanup() {
  local status=$?
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  sleep 3
  for pid in "${PIDS[@]}"; do
    kill -9 "$pid" 2>/dev/null || true
  done
  # Servers the wrapper started, in case it could not stop them
  for pid in $(pgrep -f "server.jar|proxy.jar" || true); do
    [[ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" == "$WORK"/* ]] && kill -9 "$pid" 2>/dev/null || true
  done
  if [ "$status" -eq 0 ] && [ -z "${E2E_DIR:-}" ]; then
    rm -rf "$WORK"
  else
    log "Files and logs are in $WORK"
  fi
}
trap cleanup EXIT

jar() {
  local file="$ROOT/$1/build/libs/$1-$VERSION.jar"
  [ -f "$file" ] || fail "$file is missing, run ./gradlew build first"
  echo "$file"
}

# Calls the API; prints the body and fails on an unexpected status
call() {
  local method=$1 path=$2 body=${3:-} expected=${4:-2}
  local args=(-sS -o "$WORK/response" -w '%{http_code}' -X "$method" -H "Content-Type: application/json")
  [ -n "${TOKEN:-}" ] && args+=(-H "Authorization: Bearer $TOKEN")
  [ -n "$body" ] && args+=(--data "$body")
  local status
  status=$(curl "${args[@]}" "$API$path") || fail "$method $path: no connection"
  [[ "$status" == "$expected"* ]] || fail "$method $path answered $status: $(cat "$WORK/response")"
  cat "$WORK/response"
}

# Waits until a command succeeds, checking every 2 seconds
wait_for() {
  local seconds=$1 what=$2
  shift 2
  local until=$((SECONDS + seconds))
  until "$@"; do
    [ "$SECONDS" -lt "$until" ] || fail "$what did not happen within $seconds seconds"
    sleep 2
  done
}

# Servers of a group (default: the lobby group) as name=state@port
server_state() {
  call GET /servers | jq -r --arg group "${1:-$GROUP}" \
    '[.[] | select(.server_group == $group)] | map(.server_name + "=" + .online_state + "@" + (.server_port|tostring)) | join(" ")'
}

lobby_online() {
  [[ "$(server_state)" == *"=online@"* ]]
}

log "rexlCloud $VERSION in $WORK"
mkdir -p "$WORK/master/config" "$WORK/master/modules" "$WORK/wrapper/config" "$WORK/wrapper/cache/plugins"

# Master: database, ports, the RestModule and one registered wrapper
WRAPPER_KEY=$(cat /proc/sys/kernel/random/uuid)
cp "$(jar Master)" "$WORK/master/master.jar"
cp "$(jar RestModule)" "$WORK/master/modules/RestModule.jar"
if [ "${E2E_DB_TYPE:-mysql}" = h2 ]; then
  # The embedded database of new installs, no MySQL needed
  echo '{"type": "h2", "database": "rexlcloud"}' > "$WORK/master/config/database.json"
else
  jq -n --arg host "${E2E_DB_HOST:-localhost}" --argjson port "${E2E_DB_PORT:-3306}" --arg user "${E2E_DB_USER:-root}" \
    --arg password "${E2E_DB_PASSWORD:-root}" --arg database "${E2E_DB_NAME:-rexlcloud_e2e}" \
    '{type: "mysql", host: $host, port: $port, user: $user, password: $password, database: $database}' \
    > "$WORK/master/config/database.json"
fi
jq -n --argjson port "$MASTER_PORT" '{host: "127.0.0.1", port: $port}' > "$WORK/master/config/master.json"
jq -n --arg key "$WRAPPER_KEY" '[{name: "Wrapper-1", address: "127.0.0.1", key: $key}]' > "$WORK/master/config/wrappers.json"
mkdir -p "$WORK/master/modules/RestModule"
METRICS_TOKEN=$(cat /proc/sys/kernel/random/uuid)
jq -n --argjson port "$API_PORT" --arg metrics "$METRICS_TOKEN" '{host: "127.0.0.1", port: $port, metricsToken: $metrics}'   > "$WORK/master/modules/RestModule/config.json"

# Wrapper: the master's address and the cloud plugins of this build instead of downloading them
cp "$(jar Wrapper)" "$WORK/wrapper/wrapper.jar"
EXAMPLE_JAR="$ROOT/MinestomBridge/example/build/libs/example-$VERSION.jar"
[ -f "$EXAMPLE_JAR" ] || fail "$EXAMPLE_JAR is missing, run ./gradlew build first"
cp "$(jar Bridge)" "$WORK/wrapper/cache/plugins/bridge-$VERSION.jar"
cp "$(jar Proxy)" "$WORK/wrapper/cache/plugins/proxy-$VERSION.jar"
jq -n --argjson port "$MASTER_PORT" --arg key "$WRAPPER_KEY" --argjson serverPort "$WRAPPER_PORT" \
  --argjson detached "$([ "${E2E_DETACHED:-0}" = 1 ] && echo true || echo false)" \
  '{address: "127.0.0.1", port: $port, authKey: $key, serverPort: $serverPort, detachedServers: $detached}' \
  > "$WORK/wrapper/config/wrapper.json"

# stdin stays open (the consoles read commands), output goes to the logs
start_master() {
  (cd "$WORK/master" && exec java -jar master.jar --version="$VERSION" < <(tail -f /dev/null) >> "$WORK/master.log" 2>&1) &
  PIDS+=($!)
  local before=${1:-0}
  master_api_up() {
    [ "$(grep -c "REST API listening" "$WORK/master.log")" -gt "$before" ]
  }
  wait_for 90 "The REST API starting" master_api_up
}

log "Starting the master"
start_master

PASSWORD_FILE="$WORK/master/modules/RestModule/admin-password.txt"
PASSWORD=$(tr -d '\r\n' < "$PASSWORD_FILE") || fail "no $PASSWORD_FILE"
[ -n "$PASSWORD" ] || fail "$PASSWORD_FILE is empty"
[ "$(stat -c %a "$PASSWORD_FILE")" = 600 ] || fail "$PASSWORD_FILE can be read by others"
# The console's log files must not contain it
grep -rqF "$PASSWORD" "$WORK/master/logs" && fail "the admin password is in the master's log files"
TOKEN=$(call POST /auth/login "$(jq -n --arg password "$PASSWORD" '{username: "admin", password: $password}')" | jq -r .token)
[ -n "$TOKEN" ] && [ "$TOKEN" != null ] || fail "no login token"
log "Logged in to the REST API"

start_wrapper() {
  (cd "$WORK/wrapper" && exec java -jar wrapper.jar --version="$VERSION" < <(tail -f /dev/null) >> "$WORK/wrapper.log" 2>&1) &
  PIDS+=($!)
}

log "Starting the wrapper"
start_wrapper
wrapper_connected() {
  [ "$(call GET /wrappers | jq '[.[] | select(.connected == true)] | length')" -ge 1 ]
}
wait_for 90 "The wrapper connecting" wrapper_connected
log "The wrapper is connected"

# The master loads the list of server jars in the background after its start
pick_server_jar() {
  SERVER_JAR=${E2E_SERVER_JAR:-$(call GET /server-jars \
    | jq -r '[.[] | select(.type == "SERVER" and (.name | ascii_downcase) == "paper" and .built != true)][0].id')}
  [ -n "$SERVER_JAR" ] && [ "$SERVER_JAR" != null ]
}
wait_for 120 "A Paper server jar being offered" pick_server_jar
log "Creating the group $GROUP with $SERVER_JAR"
call POST /groups "$(jq -n --arg name "$GROUP" --arg jar "$SERVER_JAR" \
  '{name: $name, type: "SERVER", memory: 1024, static: false, min_servers: 1, fallback: true, server_jar: $jar}')" > /dev/null
if [ -n "${E2E_JAVA_COMMAND:-}" ]; then
  # e.g. Java 21 for Paper 1.8; the group is created in the background, so this waits for it
  set_java() {
    curl -sS -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
      --data "$(jq -n --arg java "$E2E_JAVA_COMMAND" '{java_command: $java}')" "$API/groups/$GROUP" | grep -q '^2'
  }
  wait_for 120 "Setting the Java runtime of $GROUP" set_java
  log "$GROUP runs on $E2E_JAVA_COMMAND"
fi

wait_for 600 "The lobby coming online" lobby_online
FIRST=$(server_state)
log "Online: $FIRST"
metrics=$(curl -sS -H "Authorization: Bearer $METRICS_TOKEN" "$API/metrics") || fail "no metrics"
grep -q "^rexlcloud_servers{group=\"$GROUP\",state=\"online\"} 1$" <<<"$metrics" || fail "the metrics miss the lobby: $metrics"
log "The metrics show the lobby"

# Save the running lobby into a template variant, then read and write template files through the API
LOBBY_NAME=$(call GET /servers | jq -r --arg group "$GROUP" '[.[] | select(.server_group == $group)][0].server_name')
call POST "/servers/$LOBBY_NAME/save-template" '{"variant": "e2e-saved"}' > /dev/null
saved_template() {
  [ -f "$WORK/master/templates/$GROUP/e2e-saved/server.properties" ] && [ -d "$WORK/master/templates/$GROUP/e2e-saved/world" ]
}
wait_for 120 "$LOBBY_NAME being saved into a template" saved_template
[ ! -e "$WORK/master/templates/$GROUP/e2e-saved/cloud.json" ] || fail "cloud.json was saved into the template"
lobby_online || fail "$LOBBY_NAME is not online any more after it was saved"
grep -q "$LOBBY_NAME.*shut itself down" "$WORK/wrapper.log" && fail "$LOBBY_NAME stopped while it was saved"
log "Saved $LOBBY_NAME into the template $GROUP/e2e-saved"
curl -sS -f -X PUT -H "Authorization: Bearer $TOKEN" --data-binary "e2e upload" \
  "$API/templates/$GROUP/default/file?path=plugins/e2e.txt" > /dev/null || fail "uploading a template file failed"
[ "$(curl -sS -f -H "Authorization: Bearer $TOKEN" "$API/templates/$GROUP/default/file?path=plugins/e2e.txt")" = "e2e upload" ] \
  || fail "the uploaded template file reads differently"
call DELETE "/templates/$GROUP/e2e-saved/file" > /dev/null
log "Template files can be written, read and deleted"

# A master restart (e.g. an update) must not restart the servers: the wrapper keeps them and the master takes
# them over
plugin_connections() {
  sed 's/\x1b\[[0-9;]*m//g' "$WORK/wrapper.log" | grep -c "Server $1 connected to wrapper"
}
LOBBY=$(call GET /servers | jq -r --arg group "$GROUP" '[.[] | select(.server_group == $group)][0].server_name')
CONNECTIONS=$(plugin_connections "$LOBBY")
log "Restarting the master while $LOBBY runs"
for pid in $(pgrep -f "master.jar" || true); do
  [[ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" == "$WORK/master" ]] && kill "$pid"
done
wait_for 60 "The master stopping" bash -c "! curl -s -o /dev/null '$API/servers'"
start_master "$(grep -c "REST API listening" "$WORK/master.log")"
TOKEN=$(call POST /auth/login "$(jq -n --arg password "$PASSWORD" '{username: "admin", password: $password}')" | jq -r .token)
wait_for 120 "$LOBBY being taken over" lobby_online
grep -q "Took over" "$WORK/master.log" || fail "the master did not take over the running servers"
[ "$(plugin_connections "$LOBBY")" = "$CONNECTIONS" ] || fail "$LOBBY was restarted instead of taken over"
[ "$(server_state | wc -w)" = 1 ] || fail "a second lobby was started: $(server_state)"
log "The master took over $LOBBY without restarting it"

# With detachedServers the servers also keep running while the wrapper restarts (e.g. for an update)
if [ "${E2E_DETACHED:-0}" = 1 ]; then
  lobby_java() {
    for pid in $(pgrep -f "server.jar" || true); do
      [[ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" == "$WORK/wrapper/servers/temp/$LOBBY"_* ]] \
        && [[ "$(cat "/proc/$pid/comm" 2>/dev/null)" == java ]] && echo "$pid"
    done
    return 0
  }
  JAVA_BEFORE=$(lobby_java)
  [ -n "$JAVA_BEFORE" ] || fail "the Java process of $LOBBY was not found"
  CONNECTIONS_BEFORE=$(plugin_connections "$LOBBY")
  log "Restarting the wrapper while $LOBBY runs (process $JAVA_BEFORE)"
  for pid in $(pgrep -f "wrapper.jar" || true); do
    [[ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" == "$WORK/wrapper" ]] && kill "$pid"
  done
  sleep 5
  [ "$(lobby_java)" = "$JAVA_BEFORE" ] || fail "$LOBBY stopped with the wrapper"
  start_wrapper
  # The console highlights values with colour codes
  wrapper_log_has() {
    sed 's/\x1b\[[0-9;]*m//g' "$WORK/wrapper.log" | grep -q "$1"
  }
  wait_for 60 "The wrapper attaching to $LOBBY" wrapper_log_has "Attached to 1 server"
  plugin_reconnected() {
    [ "$(plugin_connections "$LOBBY")" -gt "$CONNECTIONS_BEFORE" ]
  }
  wait_for 90 "The plugin of $LOBBY connecting again" plugin_reconnected
  wait_for 90 "$LOBBY being online again" lobby_online
  [ "$(lobby_java)" = "$JAVA_BEFORE" ] || fail "$LOBBY was restarted instead of taken over"
  [ "$(server_state | wc -w)" = 1 ] || fail "a second lobby was started: $(server_state)"
  # Console commands go into the tmux session
  call POST "/servers/$LOBBY/command" '{"command": "say e2e-detached"}' > /dev/null
  wait_for 30 "The command reaching $LOBBY" bash -c "curl -s -H 'Authorization: Bearer $TOKEN' '$API/servers/$LOBBY/console' | grep -q e2e-detached"
  log "$LOBBY kept running through the wrapper restart"
fi

SERVER=$(call GET /servers | jq -r --arg group "$GROUP" '[.[] | select(.server_group == $group)][0].server_name')
log "Restarting $SERVER"
call POST "/servers/$SERVER/restart" > /dev/null
restarted() {
  local state
  state=$(server_state)
  [[ "$state" != *"=online@"* ]] && return 0
  return 1
}
wait_for 120 "$SERVER going down for the restart" restarted
wait_for 300 "$SERVER coming online again" lobby_online
log "Online again: $(server_state)"

log "Deleting the group $GROUP"
call DELETE "/groups/$GROUP" > /dev/null
group_gone() {
  [ -z "$(server_state)" ]
}
wait_for 180 "The servers of $GROUP being removed" group_gone
sleep 10
[ -z "$(server_state)" ] || fail "a server of the deleted group was started again: $(server_state)"
# A "Custom jar" group: nothing is downloaded and its servers wait for the server.jar in the template, here the
# MinestomBridge example server
CUSTOM_GROUP=E2EGame
log "Creating the custom jar group $CUSTOM_GROUP"
call POST /groups "$(jq -n --arg name "$CUSTOM_GROUP" \
  '{name: $name, type: "SERVER", memory: 512, static: false, min_servers: 1, fallback: false, server_jar: "CUSTOM"}')" > /dev/null
custom_waiting() {
  [[ "$(server_state "$CUSTOM_GROUP")" == *"=waiting@"* ]]
}
wait_for 60 "The custom server waiting for its jar" custom_waiting
cp "$EXAMPLE_JAR" "$WORK/master/templates/$CUSTOM_GROUP/default/server.jar"
custom_online() {
  [[ "$(server_state "$CUSTOM_GROUP")" == *"=online@"* ]]
}
wait_for 180 "The Minestom server coming online" custom_online
log "Online: $(server_state "$CUSTOM_GROUP")"
log "Deleting the group $CUSTOM_GROUP"
call DELETE "/groups/$CUSTOM_GROUP" > /dev/null
custom_gone() {
  [ -z "$(server_state "$CUSTOM_GROUP")" ]
}
wait_for 120 "The servers of $CUSTOM_GROUP being removed" custom_gone

# A proxy (Velocity, or BungeeCord with E2E_PROXY=bungeecord): it has to answer server list pings with the MOTD
# set through the API
PROXY_GROUP=E2EProxy
pick_proxy_jar() {
  PROXY_JAR=$(call GET /server-jars | jq -r --arg proxy "${E2E_PROXY:-velocity}"     '[.[] | select(.type == "PROXY" and (.name | ascii_downcase) == $proxy)][0].id')
  [ -n "$PROXY_JAR" ] && [ "$PROXY_JAR" != null ]
}
wait_for 60 "A ${E2E_PROXY:-velocity} jar being offered" pick_proxy_jar
log "Creating the proxy group $PROXY_GROUP with $PROXY_JAR"
call POST /groups "$(jq -n --arg name "$PROXY_GROUP" --arg jar "$PROXY_JAR" \
  '{name: $name, type: "PROXY", memory: 512, static: false, min_servers: 1, fallback: false, server_jar: $jar}')" > /dev/null
proxy_online() {
  [[ "$(server_state "$PROXY_GROUP")" == *"=online@"* ]]
}
wait_for 300 "The proxy coming online" proxy_online
PROXY_PORT=$(server_state "$PROXY_GROUP" | sed 's/.*@//')
log "Online: $(server_state "$PROXY_GROUP")"
MOTD_TEXT="e2e-motd-$RANDOM"
call PUT /motd "$(jq -n --arg motd "<green>$MOTD_TEXT" '{motd: $motd}')" > /dev/null
motd_shown() {
  python3 "$ROOT/e2e/ping.py" 127.0.0.1 "$PROXY_PORT" 2>/dev/null | grep -q "$MOTD_TEXT"
}
wait_for 60 "The proxy showing the new MOTD" motd_shown
log "The proxy answers pings with the new MOTD"
call DELETE "/groups/$PROXY_GROUP" > /dev/null
proxy_gone() {
  [ -z "$(server_state "$PROXY_GROUP")" ]
}
wait_for 120 "The servers of $PROXY_GROUP being removed" proxy_gone

grep -q "Exception in thread\|NullPointerException" "$WORK/master.log" && fail "exceptions in the master log"

log "Passed"
