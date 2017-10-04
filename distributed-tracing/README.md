# distributed-tracing
Tracing across distributed systems is an important method to increase visibility into the behaviour of a system which
may be made up of heterogeneous components. Xenon's in-built tracing mechanism is limited to tracing requests within
a Xenon cluster.

[OpenTracing](http://opentracing.io/), a CNCF project, provides a programming API for doing distributed tracing,
as well as a number of reference implementations such as Zipkin and Jaeger, which provide bindings for many popular
languages such as Java, Go, Python, C++, etc.

# Features
Support for [Zipkin](http://zipkin.io/) V1, Zipkin V2, and [Jaeger](https://uber.github.io/jaeger/) reporters.

# Roadmap

* Integration with Xenon request handling to pervasively trace all Operations in the cluster.
* Tracing of Lucene operations

Distributed Tracing problem is solved by popular frameworks like Zipkin [https://github.com/openzipkin/zipkin], which
comes with out of box instrumented libraries for different stacks, and language bindings for various languages like
Java, Go, Python, Scala, etc.

# Installation
Add the dstributed-tracing artifact to your Java build system.

```xml
<dependency>
  <groupId>com.vmware.xenon</groupId>
  <artifactId>distributed-tracing</artifactId>
  <version>0.0.3</version>
</dependency>
```

# Configuration.

The easy path is to provide configuration at process startup time. However, if more sophisticated configuration is
required, you may use `DTracer.setTracer` to inject arbitrarily configured OpenTracing `Tracer` instances.

## Configuration options.

| Environment variable  | JVM property          | effect 
|-----------------------|-----------------------|-------
| tracer.appName        | TRACER_APPNAME        | What name to report spans from the process under.
| tracer.implementation | TRACER_IMPLEMENTATION | What implementation to use to report spans.
| tracer.sampleRate     | TRACER_SAMPLERATE     | Override the sampling strategy of your implementation.
| tracer.zipkinUrl      | TRACER_ZIPKINURL      | Provide a URL for Zipkin to report to.

Where the underlying library - for instance jaeger-client-java - has their own parameters, distributed-tracing will
honour those where possible.

## Sampling

Some reporters require static configuration of sampling. For those, the TRACER_SAMPLERATE parameter may be used to
provide that upfront.

# Instrumenting operations in Xenon.

Example:
```java
import com.vmware.xenon.common.opentracing.TracerFactory;
import io.opentracing.*;

// Takes care of https://github.com/opentracing/opentracing-java#initialization
Tracer tracer = TracerFactory.factory.create();

// Create Local Spans around code you want to call out
// https://github.com/opentracing/opentracing-java#starting-a-new-span

public void handlerFunc() {
    try (ActiveSpan activeSpan = tracer.buildSpan("someWork").startActive()) {
        // Do things.
        //
        // If we create async work, `activeSpan.capture()` allows us to pass the `ActiveSpan` along as well.
        final ActiveSpan.Continuation cont = activeSpan.capture();
            doAsyncWork(new Runnable() {
                @Override
                public void run() {
        
                    // use the Continuation to reactivate the Span in the callback.
                    try (ActiveSpan activeSpan = cont.activate()) {
                        ...
                    }
                }
            });
    }
}
```
# Changelog
[Changelog](CHANGELOG.md)