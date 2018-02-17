FROM openjdk:8-jdk

WORKDIR /ompldr
EXPOSE 8080

RUN apt-get update \
  && apt-get upgrade -qq -y \
  && apt-get install -qq -y gettext

COPY . /build
RUN cd /build \
  && cp start.sh /ompldr \
  && ./gradlew build \
  && mkdir -p /ompldr \
  && cp build/libs/ompldr-server.jar /ompldr \
  && rm -rf $HOME/.gradle \
  && apt-get remove -qq -y --purge build-essential autoconf automake \
    libtool autoconf-archive libbsd-dev python-dev \
  && rm -rf /var/lib/apt/lists/* \
  && rm -rf /build

ENTRYPOINT ["/ompldr/start.sh"]
