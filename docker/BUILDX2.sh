#!/bin/bash

export JAVA_HOME=`/usr/libexec/java_home -v 21`

( cd ..; mvn clean package  )
cp ../target/l9g-oidc-info.jar .

TAGS=""

while (( $# )); do
  TAGS="$TAGS --tag ghcr.io/thorsten-l/l9g-oidc-info:$1"
  TAGS="$TAGS --tag tludewig/l9g-oidc-info:$1"
  shift
done

BUILDING_TAGS=$(echo $TAGS | tr ' ' "\n")

../private/LOGIN.sh

docker buildx build --progress plain --no-cache \
  --push \
  --platform linux/arm64,linux/amd64 $BUILDING_TAGS .
