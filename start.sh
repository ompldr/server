#!/bin/sh

# Substitute environment variables
envsubst < /etc/ompldr-config/application.conf.in > application.conf

cat application.conf

exec java -server \
  -Xms3g \
  -Xmx3g \
  -XX:+UseConcMarkSweepGC \
  -XX:+UseStringDeduplication \
  -XX:+UseCompressedOops \
  -Dconfig.file=application.conf \
  $JVM_OPTS \
  -jar ompldr-server.jar \
  "$@"
