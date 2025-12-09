# Hardcover Data Sync

Script to sync all user data from [Hardcover](https://hardcover.app)
to a local directory.

```
hardcover-data-sync --bearer=<token> backup/
```

On each run, the tool will clear the destination directory and write out files.
The data will not be versioned in any way. If you want historical versions
use a log rotation tool, ZFS snapshots, or something else of that nature.

Any data missing that you want included?
[File an issue!](https://github.com/JakeWharton/hardcover-data-sync/issues/new)

Your bearer token can be found by visiting
[hardcover.app/account/api](https://hardcover.app/account/api) when logged in.
The token is everything after "`Bearer `" in the large text box.
This value should be treated as a secret.


## Install

**Mac OS**

```
brew install JakeWharton/repo/hardcover-data-sync
```

**Other**

Download ZIP from [latest release](https://github.com/JakeWharton/hardcover-data-sync/releases/latest) and
run `bin/hardcover-data-sync` or `bin/hardcover-data-sync.bat`.


## Usage

```
$ hardcover-data-sync -h
Usage: hardcover-data-sync [<options>] <dir>

  Download all user data from Hardcover into a folder for backup

Options:
  --bearer=<token>  Bearer token for HTTP 'Authorization' header
  -h, --help        Show this message and exit

Arguments:
  <dir>  Directory into which the data will be written
```


## Docker

A container which runs the binary is available from Docker Hub and GitHub Container Registry.

* `jakewharton/hardcover-data-sync`
* `ghcr.io/jakewharton/hardcover-data-sync`

[![Docker Image Version](https://img.shields.io/docker/v/jakewharton/hardcover-data-sync?sort=semver&style=flat-square)][hub]
[![Docker Image Size](https://img.shields.io/docker/image-size/jakewharton/hardcover-data-sync?sort=semver&style=flat-square)][hub]<br>
[![Docker Image Version](https://img.shields.io/docker/v/jakewharton/hardcover-data-sync/trunk?style=flat-square)][hub]
[![Docker Image Size](https://img.shields.io/docker/image-size/jakewharton/hardcover-data-sync/trunk?style=flat-square)][hub]

 [hub]: https://hub.docker.com/r/jakewharton/hardcover-data-sync/

```
docker run --rm
    -v /path/to/data:/data \
    jakewharton/hardcover-data-sync \
      --bearer ... \
      /data
```

See [command-line usage](#Usage) for how to run the binary.

If you specify the `--cron` option with a valid cron specifier, the tool will not exit and perform automatic checks in accordance with the schedule.
For help creating a valid cron specifier, visit [cron.help](https://cron.help/#0_*_*_*_*).

To be notified when sync is failing visit https://healthchecks.io, create a check, and specify the ID to the container using the `--hc-id` option.
You can also specify a custom host with `--hc-host`.

If you're using Docker Compose, all the options are available as environment variables.

```yaml
services:
  hardcover-data-sync:
    image: jakewharton/hardcover-data-sync
    restart: unless-stopped
    volumes:
      - /path/to/data:/data
    environment:
      - "HARDCOVER_SYNC_CRON=0 * * * *"
      - "HARDCOVER_SYNC_BEARER=..."
      #Optional:
      - "HARDCOVER_SYNC_HC_ID=..."
      - "HARDCOVER_SYNC_HC_HOST=..."
```

Note: You may want to specify an explicit version rather than `latest`.
See https://hub.docker.com/r/jakewharton/hardcover-data-sync/tags or `CHANGELOG.md` for the available versions.
Use `trunk` for the latest changes.

## Development

To run the latest code build with `./gradlew installDist`.  This will put the application into
`build/install/hardcover-data-sync/`. From there you can use the
[command-line instructions](#Usage) to run.

The Docker containers can be built with `docker build .`, which also runs the full set of checks
as CI would.

# License

    Copyright 2024 Jake Wharton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
