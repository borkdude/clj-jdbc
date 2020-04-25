FROM clojure:lein-2.9.1 AS BASE
ARG CLJ-JDBC_XMX="-J-Xmx3g"

RUN apt update
RUN apt install --no-install-recommends -yy curl unzip build-essential zlib1g-dev
WORKDIR "/opt"
RUN curl -sLO https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.1/graalvm-ce-java8-linux-amd64-19.3.1.tar.gz
RUN tar -xzf graalvm-ce-java8-linux-amd64-19.3.1.tar.gz

ENV GRAALVM_HOME="/opt/graalvm-ce-java8-19.3.1"
ENV JAVA_HOME="/opt/graalvm-ce-java8-19.3.1/bin"
ENV PATH="$PATH:$JAVA_HOME"
ENV CLJ-JDBC_STATIC="true"
ENV CLJ-JDBC_XMX=$CLJ-JDBC_XMX

COPY . .
RUN ./script/compile


FROM alpine:latest

RUN apk add --no-cache curl
RUN mkdir -p /usr/local/bin
COPY --from=BASE /opt/bb /usr/local/bin/bb
CMD ["bb"]
