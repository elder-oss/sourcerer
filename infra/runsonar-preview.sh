#!/usr/bin/env bash
if [ -n "$CI_PULL_REQUEST" ]
then
  echo "Running Sonar in preview mode for Pull Request $CI_PULL_REQUEST"
  TERM="dumb" ./gradlew sonarqube -Dsonar.analysis.mode=preview -Dsonar.github.pullRequest=${CI_PULL_REQUEST##*/}
else
  echo "Running Sonar in preview mode for (no PR)"
  TERM="dumb" ./gradlew sonarqube -Dsonar.analysis.mode=preview
fi
