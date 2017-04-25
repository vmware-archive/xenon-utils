#JavaEE Adapter
A set of utilities to enable contract (interface) based services development using Xenon framework.  
All the utilities are primarily focused to enable 
- Request - Response model based development than callback ie., CompletableFuture instead of registering callbacks.
- Use Java EE familiar concepts wherever possible
- Contract & verify based development. More details at [spring-cloud-contract.html] (https://cloud.spring.io/spring-cloud-contract/spring-cloud-contract.html) 

Provides following features
- JAX RS Annotations Support both at the client and server side
- State machines
- Query Utils
- CDI using GUICE


## JAX RS Annotations Support

Inspired by JAX-RS proxy based client builder, this extension provides JAX-RS annotation processing ability to 

- on server side to route request to appropriate methods in a stateless services (RequestRouterBuilder)
- on the client side to interact with Xenon services from client perspective (JaxRsServiceClient)


This extension completely uses Operation for handling request and doesnt depend on any other external http libraries.

<b>Limitation </b>
The intention of this extension is not to provide or implement the whole JAX-RS spec on top of Xenon.

- We support only a sub-set of annotation from client perspective
- Usage of these in server side requires some understanding of Xenon framework

Note: Test cases will serve as a usage document

Currently supported annotations in client side are 

- GET/POST/DELETE/PUT/DELETE
- Custom annotation PATCH as jaxrs spec doesn't have one
- HEADER/COOKIE/DEFAULT
- Custom annotation @OperationBody to receive body as a payload

### Producer Side usage

First step is declare your *service contract* like below (This is completely optional but highly recommended )
```java
@Path(SELF_LINK)
public interface SampleService {

    String SELF_LINK = "/xenon-ext/sample";

    /**
    * Example of asynchronous service
    * Employee response will be send only when the CompletableFuture is complete and hence provides async capability in server side
    */
    @Path("/category/{id}")
    @GET
    CompletableFuture<Employee> fetchSample(@PathParam("id") String id, @QueryParam("tags") String tag);

    /**
    * Example of a POST operation. 
    * Employee POJO can have javax validation annotations and it will be validated before the method is invoked
    */
    @POST
    CompletableFuture<Employee> newEmployee(@OperaionBody Employee employee);
}
```

Second step is to Implement the service by extending JaxRsBridgeStatelessService. * Note the use of StatefulServiceContract*
```java
public class SampleServiceImpl extends JaxRsBridgeStatelessService implements SampleService {
    
    // A default contract applicable for all the stateful services
    private StatefulServiceContract<Employee> employeeSvc;
    
     public SampleServiceImpl() {
          // this is required only if you implement interface containing JAX RS annotations. 
          // this instructs to process the annotations from SampleService interface along with current service class 
          setContractInterface(SampleService.class); 
     }
     
      @Override
      protected void initializeInstance() {
          employeeSvc = ServiceConsumerUtil.newStatefulSvcContract(getHost(), EmployeeService.FACTORY_LINK, Employee.class);
      }
    
    public CompletableFuture<Employee> fetchSample(@PathParam("id") String id, @QueryParam("tags") String tag) {
         // do implementation
         // do things only async'ly
         return null;
    }
    //other methods
    
    // Can define additional public methods other than implementing interface too. Those should have JAX RS annotations. 
    @PUT
    @Path("/{id}")
    CompletableFuture<Employee> updateEmployee(@PathParam("id") String id,@OperaionBody Employee employee) {
         // delegate to a statefule service
         return employeeSvc.put(id,employee);
    }
}
```
Now you have a fully functional REST service using Xenon

### Consumer Side usage
When you want to use the proxy client builder, you need an interface with JAX-RS annotations like the one declared above.

Example usage with in Xenon host, when the sample service is running in same host
```java
       SampleService sampleService = JaxRsServiceConsumer.newBuilder()
                 .withHost(host)
                 .withResourceInterface(FullSampleService.class)
                 .build();
```
Example usage with in Xenon host, when the sample service is hosted externally
```java
       SampleService sampleService = JaxRsServiceClient.newBuilder()
                 .withHost(host)
                 .withBaseUri('http://someServiceHost:8000')
                 .withResourceInterface(FullSampleService.class)
                 .build();
```
Example usage outside Xenon host
```java
       SampleService sampleService = JaxRsServiceClient.newBuilder()
                 .withBaseUri('http://someServiceHost:8000')
                 .withResourceInterface(FullSampleService.class)
                 .build();
```


## Query Utils

Similar to @NamedQuery of JPA, this utility supports declaring following annotations on an interface. 
- @ODataQuery
- @ODataPagedQuery

Following details can be specified in ODataQuery annotation at the moment.
- Attribute value captures the filter criteria
- Attribute documentKind decides the return type of the query results. Filter criteria is enriched with documentKind info
- Attribute top defines the no. of return values. Defaults to 9999
- Attribute orderBy defines the order by clause
- Attribute pickFirst ensures the return type is a single document and not a collection
Read the JavaDoc of ODataQuery & ODataPagedQuery for further details

**Note :** Returning multiple type of service document is not supported at the moment
 
Example :
```java

public interface ExampleODataXenonQueryService extends XenonQueryService {

    /**
     * Return types can be Set<ExampleServiceState>, List<ExampleServiceState>, Collection<ExampleServiceState> or ExampleServiceState[]
     * when you are expecting multiple values 
     */
    @ODataQuery(value = "name eq :name", documentKind = ExampleServiceState.class, orderBy = "counter desc", orderByType = "LONG")
    CompletableFuture<List<ExampleServiceState>> findByNameAndOrderDescByCounter(@Param("name") String name);

    /**
     * Return type can be single document type when pickFirst option is true or top = 1 
     */
    @ODataQuery(value = "name eq :name and required eq :required", documentKind = ExampleServiceState.class, pickFirst = true)
    CompletableFuture<ExampleServiceState> findByNameAndRequired(@Param("name") String name, @Param("required") String required);

    /**
     * Return type has to be  CompletablePagedStream which exposes Stream kind of APIs to interact with all the results 
     */
    @ODataPagedQuery(value = "name eq :name", limit = 5, documentKind = ExampleServiceState.class)
    CompletablePagedStream<ExampleServiceState> findByNameWithLimit(@Param("name") String name);

}

```

Next step is accessing these queries.

```java
public class DemoODataQuery {
    
     public CompletableFuture<ExampleServiceDocument> doSomeOp() {
         //...
         ExampleODataXenonQueryService exampleQuerySvc = JaxRsServiceClient.newBuilder()
                          .withHost(host)
                          .withResourceInterface(ExampleODataXenonQueryService.class)
                          .build();
         exampleQuerySvc.findFirstByName(name); // this will return the results
     }

 
 }

```
Take a look at test cases for more examples. The above can be simplified further using CDI as shown below
```java

public class DemoODataQueryWithCDI {
    
    //....
    @InjectRestProxy
    private ExampleODataXenonQueryService exmapleQuerySvc;
    
    public CompletableFuture<ExampleServiceDocument> doSomeOp(@QueryParam("name") String name){
        return exmapleQuerySvc.findFirstByName(name);
    }
}

```