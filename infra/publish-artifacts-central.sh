#!/usr/bin/env bash
export TERM="dumb"
set -e
echo $ELDER_KEYRING | base64 --decode > /tmp/keyring
./gradlew uploadArchives closeRepository -Penable_signing=true

echo "Waiting for Nexus to close staging repository"
sleep 10

echo "Promoting repository"
./gradlew promoteRepository
