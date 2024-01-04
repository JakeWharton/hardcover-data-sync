#!/usr/bin/with-contenv sh

if [ -n "$HEALTHCHECK_ID" ]; then
	curl -sS -X POST -o /dev/null "$HEALTHCHECK_HOST/$HEALTHCHECK_ID/start"
fi

# If the binary fails we want to avoid triggering the health check.
set -e

# shellcheck disable=SC2086
/app/bin/hardcover-data-sync \
	--bearer "$HARDCOVER_BEARER"  \
	$HARDCOVER_DATA_SYNC_ARGS \
	/data

# Print something since the script otherwise has no output if nothing changes.
echo "Check complete!"

if [ -n "$HEALTHCHECK_ID" ]; then
	curl -sS -X POST -o /dev/null --fail "$HEALTHCHECK_HOST/$HEALTHCHECK_ID"
fi
