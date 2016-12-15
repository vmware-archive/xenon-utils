# Introduction
Xenon is growing fast, and a lot of teams (like Photon Controller) have come forward and are building their Micro-services using Xenon. Xenon is solving a lot many distributed problems like HA, Scaling Out, Versioning etc, right out of the box. But there are other frameworks like Spring Boot etc, which are also being used to write micro-services (due to various reasons like team prior knowledge etc).

Distributed Tracing is a cross-cutting concern for all services no matter which technology stack they’re implemented in. Today, teams want to carry out performance analysis and anomaly detection over distributed application stacks. And this distributed-tracing library will bring differentiated value to Xenon by extending over the current Operation Tracing capabilities offered by Xenon.
Distributed Tracing problem is solved by popular frameworks like Zipkin [https://github.com/openzipkin/zipkin], which comes with out of box instrumented libraries for different stacks, and language bindings for various languages like Java, Go, Python, Scala, etc.

The current version of library submits traces to Zipkin, and the upcoming versions would extend the library to submit traces to Xenon and watch the distributed traces right in Operation Tracing Console.

# How to use
This library leverages the Zipkin Brave libraries and helps the users to instrument their code with minimal changes. It has 3 different functionalities for local, client & server spans, as explained here http://zipkin.io/pages/instrumenting.html. It assumes that Zipkin Server is ready and available for trace submissions.
Here are the key things to remember:
- To use the library, the user will simply instantiate the DTracer, just like Logger, and can create different spans. The Tracer would figure out where and how of trace submission, based on the following jvm properties, which should be provided as jvm arguments - tracer.appName, tracer.sampleRate & tracer.zipkinUrl. If those are not provided, internally it will try reading from env variables - TRACER_APP_NAME, TRACER_SAMPLE_RATE & TRACER_ZIPKIN_URL. And if that fails, tracing would be a no-op.
- The library takes care of tracing for nested spans and works well for highly asynchronous services like Xenon based services, with very little overhead. It uses Google Guava Cache internally to keep a track of all the spans started as part of asynchronous operations.
- The key difference w.r.t. Xenon's Operation Tracing is, instead of the tracing fields - Context-Id & Referer, this library submits distributed tracing context ids as a combo of - Trace-Id, Span-Id & Parent Span-Id (much like Zipkin). You can call it as “distributed tracing context id”. This enables wiring the traces using these new fields and allow users to get a complete distributed tracing picture on Zipkin UI.
- To support future use cases when Xenon Operation UI would be used to wire the traces and get a full distributed tracing picture, a convenient method is added at DTracer.startServerSpanAndGenerateContextId, which would return a Json formatted distributed tracing context id, which can be set into Xenon’s Operation as a parameter to setContextId. If this is how the context id is set for an Operation, then when Xenon’s Operation Tracing is enabled, you would start observing that the contextId field with the distributed trace ids. This would be used in future to show up on Operations UI.

Example:
```
public static final Tracer tracer = Tracer.getTracer();
// Create Local Spans:
public void handleFunc() {
    SpanId spanId = this.tracer.startLocalSpan(this.getClass().getName(), "handleFunc");
    ……
    ……
    tracer.endLocalSpan(spanId);
}
```
# Changelog
[Changelog](CHANGELOG.md)