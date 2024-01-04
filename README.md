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

The container runs the tool using cron on a specified schedule.

[![Docker Image Version](https://img.shields.io/docker/v/jakewharton/hardcover-data-sync?sort=semver)][hub]
[![Docker Image Size](https://img.shields.io/docker/image-size/jakewharton/hardcover-data-sync)][layers]

 [hub]: https://hub.docker.com/r/jakewharton/hardcover-data-sync/
 [layers]: https://microbadger.com/images/jakewharton/hardcover-data-sync

```
docker run -it --rm
    -v /path/to/data:/data \
    -e "CRON=0 * * * *" \
    -e "HARDCOVER_BEARER=..." \
    jakewharton/hardcover-data-sync:0.1
```

To be notified when sync is failing visit https://healthchecks.io, create a check, and specify
the ID to the container using the `HEALTHCHECK_ID` environment variable.

### Docker Compose

```yaml
services:
  hardcover-data-sync:
    image: jakewharton/hardcover-data-sync:0.1
    restart: unless-stopped
    volumes:
      - /path/to/data:/data
    environment:
      - "CRON=0 * * * *"
      - "HARDCOVER_BEARER=..."
      #Optional:
      - "HEALTHCHECK_ID=..."
      - "PUID=..."
      - "PGID=..."
```


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
