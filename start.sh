#!/bin/sh

# Substitute environment variables
envsubst < /etc/ompldr-config/application.conf.in > application.conf

cat application.conf

exec java -server \
  -Xms2g \
  -Xmx2g \
  -XX:+UseConcMarkSweepGC \
  -XX:+UseStringDeduplication \
  -XX:+UseCompressedOops \
  -Dconfig.file=application.conf \
  $JVM_OPTS \
  -jar ompldr-server.jar \
  "$@"
