FROM symphony-docker-local.jfrog.io/symphony/symphony-base:jre-8u141
ADD target/xenon-distributed-tracing-0.0.3-SNAPSHOT-jar-with-dependencies.jar /
RUN echo "hosts: files dns" > /etc/nsswitch.conf
ENV TRACER_SAMPLERATE=1.0 TRACER_APPNAME=demo TRACER_IMPLEMENTATION=jaeger
ENTRYPOINT exec java -jar /xenon-distributed-tracing-0.0.3-SNAPSHOT-jar-with-dependencies.jar --port=8000 --sandbox=/tmp/sandbox --bindAddress=0.0.0.0 --id=1234
EXPOSE 8000/tcp
