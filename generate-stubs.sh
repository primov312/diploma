#!/usr/bin/env bash
set -e  # Stop if anything fails

# Paths
GENERATOR_JAR="openapi-generator-cli.jar"
OPENAPI_SPEC="config/api/openapi.yaml"
TARGET_REPO="../rocket-credit-user-data"
GENERATOR_VERSION="6.6.0"

echo "🔧 Checking for OpenAPI Generator JAR..."
if [[ ! -f $GENERATOR_JAR ]]; then
  echo "🌐 Downloading OpenAPI Generator JAR v$GENERATOR_VERSION..."
  curl -L -o $GENERATOR_JAR "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$GENERATOR_VERSION/openapi-generator-cli-$GENERATOR_VERSION.jar"
fi

echo "🚀 Generating stubs for Gateway..."
java -jar $GENERATOR_JAR generate \
  -i $OPENAPI_SPEC \
  -g spring \
  -o $TARGET_REPO \
  --api-package com.rocketcredit.rocket-credit-user-data.api \
  --model-package com.rocketcredit.rocket-credit-user-data.model \
  --invoker-package com.rocketcredit.rocket-credit-user-data.invoker \
  --group-id com.rocketcredit \
  --artifact-id rocket-credit-user-data \
  --artifact-version 0.1.0-SNAPSHOT \
  --additional-properties=interfaceOnly=true,swaggerAnnotations=true

echo "✅ Generation complete! Stubs are in $TARGET_REPO"
