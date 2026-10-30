#!/usr/bin/env bash
# Back up every database and role to S3. Run nightly by cron on the server, as ubuntu:
#   crontab -e   and add (server clock is UTC, so 20:00 = 03:00 in Vietnam):
#   0 20 * * * $HOME/visionfit-backend/infra/ec2/backup.sh <bucket> >> $HOME/backups/backup.log 2>&1
# Needs aws-cli (sudo snap install aws-cli --classic) and an IAM role on the instance that may
# s3:PutObject into <bucket>. Local copies older than 7 days are deleted; on S3, lifecycle rules
# expire daily/ after 7 days, weekly/ after 28 and monthly/ after 90 (set them on the bucket once).
# Optional: BACKUP_HEALTHCHECK_URL in .env, pinged after a successful night (Healthchecks.io).
set -euo pipefail
cd "$(dirname "$0")/../.."
export PATH="/snap/bin:$PATH"   # cron's PATH does not include snap's aws
bucket="${1:?usage: backup.sh <s3-bucket>}"
file="$HOME/backups/visionfit-$(date +%F-%H%M).sql.gz"
# Healthchecks.io ping URL, kept in .env (out of git: anyone with it can report a fake success).
healthcheck_url="$(grep -s '^BACKUP_HEALTHCHECK_URL=' .env | cut -d= -f2- || true)"

mkdir -p "$HOME/backups"
# Written as .part first: a dump that fails halfway never looks like a good backup.
docker compose -f docker-compose.prod.yml exec -T postgres pg_dumpall -U postgres | gzip > "$file.part"
mv "$file.part" "$file"
# Which retention tier tonight's backup belongs to; the bucket's lifecycle rules expire each folder
# on their own, so the server never needs to delete. The 1st wins over Sunday: every month keeps one.
if [ "$(date +%d)" = "01" ]; then
    tier=monthly
elif [ "$(date +%u)" = "7" ]; then
    tier=weekly
else
    tier=daily
fi
aws s3 cp "$file" "s3://$bucket/$tier/" --region ap-southeast-2 --no-progress
find "$HOME/backups" -name 'visionfit-*' -mtime +7 -delete
echo "$(date '+%F %T') ok $tier $(du -h "$file" | cut -f1)"
# Heartbeat, reached only when every step above succeeded (set -e): a night without it, whatever the
# cause (failed dump, lost S3 access, cron or server down), makes Healthchecks.io email us.
if [ -n "$healthcheck_url" ]; then
    curl -fsS -m 10 --retry 3 "$healthcheck_url" > /dev/null
fi
