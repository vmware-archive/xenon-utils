# xenon-swagger-adapter
Xenon-swagger2 package will server a [Swagger 2.0](http://swagger.io/specification/) compatible description of the services running in a ServiceHost.

## Usage
Add the dependency to the classpath:
```xml
<dependency>
  <groupId>com.vmware.xenon</groupId>
  <artifactId>xenon-swagger2</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Then start the service which will serve the descriptor and the SwaggerUI:
```java
SwaggerDescriptorService swagger = new SwaggerDescriptorService();
//
Info info = new Info();
info.setDescription("My awesome API description");
info.setTermsOfService("Terms of service");
info.setTitle("Xenon API");
info.setVersion("20016-01");

swagger.setInfo(info);

// exclude services you regard as not-public
swagger.setExcludedPrefixes("/core/authz/", "/crontab/");

// start service
host.startService(swagger);
```

The descriptor can be found on `/discovery/swagger` and the SwaggerUI on `/discovery/swagger/ui`.
You can also run the `com.vmware.xenon.swagger.ExampleServiceHostWithSwagger` example for a quick test drive.