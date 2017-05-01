#!/bin/bash -xe

cd xenon
# sed -i -e 's/ServiceOption\.INSTRUMENTATION/ServiceOption\.IDEMPOTENT_POST/g' ./xenon-common/src/main/java/com/vmware/xenon/services/common/ExampleService.java
./mvnw package -DskipTests -DskipAnalysis=true
cd ..
cp -f xenon/xenon-host/target/xenon-host-*-SNAPSHOT-jar-with-dependencies.jar jepsen/docker/node/
cd jepsen/docker
./up.sh --time-limit 60 --concurrency 10
