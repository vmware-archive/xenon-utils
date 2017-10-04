FROM openjdk:8-jre
ADD target/xenon-distributed-tracing-0.0.3-SNAPSHOT-jar-with-dependencies.jar /
ENV JAEGER_SERVICE_NAME=demo JAEGER_SAMPLER_TYPE=const JAEGER_SAMPLER_PARAM=1 XENON_TRACER_FACTORY_PROVIDER=jaeger
ENTRYPOINT exec java -jar /xenon-distributed-tracing-0.0.3-SNAPSHOT-jar-with-dependencies.jar --port=8000 --sandbox=/tmp/sandbox --bindAddress=0.0.0.0 --id=1234
EXPOSE 8000/tcp
