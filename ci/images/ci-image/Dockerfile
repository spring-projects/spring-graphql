FROM ubuntu:focal-20220531

ADD setup.sh /setup.sh
ADD get-jdk-url.sh /get-jdk-url.sh
ADD get-docker-url.sh /get-docker-url.sh
RUN ./setup.sh java17

ENV JAVA_HOME /opt/openjdk
ENV PATH $JAVA_HOME/bin:$PATH
ADD docker-lib.sh /bin/docker-lib.sh
ADD start-docker.sh /bin/start-docker.sh
