# Backup sidecar for the self-hosted stack (#337, ADR-0025).
#
# Built on postgres:18 deliberately: pg_dump refuses to dump a server newer
# than itself, so the tool version and the server version have to move
# together. Sharing the base image is what keeps them from drifting apart on
# the next PostgreSQL upgrade.
FROM postgres:18

# age encrypts before anything leaves the house; rclone is the single transport
# for both destinations, so a tailnet host and an object store are two remotes
# rather than two code paths.
RUN apt-get update \
    && apt-get install -y --no-install-recommends age rclone ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY scripts/ /usr/local/bin/
RUN chmod +x /usr/local/bin/*.sh

# Never run as the postgres user's default database chores; this image only
# ever runs the scripts it was given.
ENTRYPOINT []
CMD ["/usr/local/bin/schedule.sh"]
