#!/usr/bin/env bash
export TERM="dumb"
echo $ELDER_KEYRING | base64 --decode > /tmp/keyring
./gradlew uploadArchives closeRepository

echo "Waiting for Nexus to close staging repository"
sleep 10

echo "Promoting repository"
./gradlew promoteRepository
