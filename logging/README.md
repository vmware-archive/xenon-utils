# logging
Xenon uses java.util.logging internally. However many 3rd party libraries choose other logging facades/framework.s
This project implements a binding for SLF4j that delegates to the logging subsystem of a XenonHost.

# Usage
Just add the Maven dependency to your classpath. Make sure there are no other bindings in the classpath for example slf4j-nop, slf4j-log4j12 etc.

```
<dependency>
  <groupId>com.vmware.xenon</groupId>
  <artifactId>slf4j-xenon</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

If a dependency of yours uses jcl you can use [jcl-over-slf4j](http://www.slf4j.org/legacy.html) to tunnel
logging calls to slfj4 which will delegate logging to Xenon/JUL.

# Changelog
[Changelog](CHANGELOG.md)