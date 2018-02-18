#!/bin/sh

# Substitute environment variables
envsubst < /etc/ompldr-config/application.conf.in > application.conf

cat application.conf

OMPLDR_SERVER_OPTS="-Dconfig.file=application.conf"
JAVA_OPTS="-XX:+UseCompressedOops $JAVA_OPTS"
JAVA_OPTS="-XX:+UseStringDeduplication $JAVA_OPTS"
JAVA_OPTS="-XX:+UseConcMarkSweepGC $JAVA_OPTS"
JAVA_OPTS="-Xmx2g $JAVA_OPTS"
JAVA_OPTS="-Xms2g $JAVA_OPTS"
JAVA_OPTS="-server $JAVA_OPTS"
exec java $JAVA_OPTS $OMPLDR_SERVER_OPTS -jar ompldr-server.jar \
  "$@"
