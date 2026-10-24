#!/usr/bin/env bash
# The nginx lab experiments as one-word commands. Run from Git Bash in visionfit-backend:
#   bash infra/nginx/lab.sh e1 | e2 [gateway] | e3 | e4
# Every request is printed before it is sent, so you still see exactly what goes over the wire.
set -u

NGINX=http://127.0.0.1:8000
GATEWAY=http://127.0.0.1:${GATEWAY_PORT:-8090}
TODAY=$(date +%F)

run() {
    echo "+ $*"
    "$@"
    echo
}

case "${1:-}" in
    e1)
        echo "E1: through nginx. Compare the client IP in nginx's log (nginx.cmd log) and in the gateway's log."
        run curl -sS -i "$NGINX/api/v1/meals/streak?today=$TODAY"
        ;;
    e2)
        base=$NGINX
        [ "${2:-}" = "gateway" ] && base=$GATEWAY
        echo "E2: 22 wrong logins to $base, each with a different made-up X-Forwarded-For."
        echo "+ curl -X POST $base/api/v1/auth/login -H 'X-Forwarded-For: 9.9.9.<1..22>' ..."
        for i in $(seq 1 22); do
            curl -s -o /dev/null -w "%{http_code} " -X POST "$base/api/v1/auth/login" \
                -H "Content-Type: application/json" -H "X-Forwarded-For: 9.9.9.$i" \
                -d '{"email":"lab@example.com","password":"wrong-pass"}'
        done
        echo
        ;;
    e3)
        echo "E3: a 7 MB upload, over nginx's 6 MB limit. Does the gateway log a line for it?"
        head -c 7000000 /dev/urandom > lab-big.bin
        run curl -s -o /dev/null -w "%{http_code}\n" -X POST "$NGINX/api/v1/analysis" -F "photo=@lab-big.bin;type=image/jpeg"
        rm -f lab-big.bin
        ;;
    e4)
        echo "E4: stop the gateway first. Who answers now, and is the body JSON or HTML?"
        run curl -sS -i "$NGINX/api/v1/meals/streak?today=$TODAY"
        ;;
    *)
        echo "usage: bash infra/nginx/lab.sh e1 | e2 [gateway] | e3 | e4"
        exit 1
        ;;
esac
