#!/bin/bash
set -e

source $(dirname $0)/common.sh
repository=$(pwd)/distribution-repository

/bin/start-docker.sh
if [[ -n $DOCKER_HUB_USERNAME ]]; then
	echo "$DOCKER_HUB_PASSWORD" | docker login -u $DOCKER_HUB_USERNAME --password-stdin
fi

pushd git-repo > /dev/null
./gradlew -Dorg.gradle.internal.launcher.welcomeMessageEnabled=false --no-daemon --max-workers=4 -PdeploymentRepository=${repository} build publishAllPublicationsToDeploymentRepository
popd > /dev/null
