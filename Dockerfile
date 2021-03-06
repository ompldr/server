FROM openjdk:8-jdk

WORKDIR /opt/ompldr
EXPOSE 8080

RUN apt-get update \
  && apt-get upgrade -qq -y \
  && apt-get install -qq -y gettext \
  && rm -rf /var/lib/apt/lists/*

COPY . /build
RUN cd /build \
  && ./gradlew shadowJar \
  && mkdir -p /opt/ompldr \
  && cp ./build/libs/ompldr-server.jar /opt/ompldr/ \
  && cp build/libs/ompldr-server.jar /ompldr \
  && rm -rf $HOME/.gradle \
  && rm -rf /build

COPY start.sh /opt/ompldr/start.sh

CMD ["/opt/ompldr/start.sh"]
