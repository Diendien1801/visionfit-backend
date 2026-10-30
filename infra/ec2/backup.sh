#!/usr/bin/env bash
# Back up every database and role to S3. Run nightly by cron on the server, as ubuntu:
#   crontab -e   and add (server clock is UTC, so 20:00 = 03:00 in Vietnam):
#   0 20 * * * $HOME/visionfit-backend/infra/ec2/backup.sh <bucket> >> $HOME/backups/backup.log 2>&1
# Needs aws-cli (sudo snap install aws-cli --classic) and an IAM role on the instance that may
# s3:PutObject into <bucket>. Local copies older than 7 days are deleted; S3 keeps its own.
set -euo pipefail
cd "$(dirname "$0")/../.."
export PATH="/snap/bin:$PATH"   # cron's PATH does not include snap's aws
bucket="${1:?usage: backup.sh <s3-bucket>}"
file="$HOME/backups/visionfit-$(date +%F-%H%M).sql.gz"

mkdir -p "$HOME/backups"
# Written as .part first: a dump that fails halfway never looks like a good backup.
docker compose -f docker-compose.prod.yml exec -T postgres pg_dumpall -U postgres | gzip > "$file.part"
mv "$file.part" "$file"
aws s3 cp "$file" "s3://$bucket/daily/" --region ap-southeast-2 --no-progress
find "$HOME/backups" -name 'visionfit-*' -mtime +7 -delete
echo "$(date '+%F %T') ok $(du -h "$file" | cut -f1)"
