# VMware has ended active development of this project, this repository will no longer be updated.

# xenon-utils
This repo holds libraries/utilities which will make it easy for users to solve very specific problems and complement Xenon's capabilities. It is being developed as a Xenon Peer Project and will be primarily used to provide inter-operabilities with various non-xenon solutions/frameworks. At the start the project would include libraries like slf4j, distributed-tracing, etc, which helps interact with other frameworks.

## Projects
* [Distributed tracing](distributed-tracing/README.md) Distributed tracing with Zipkin
* [Logging](logging/README.md) Integrate Java logging frameworks with Xenon
* [xenonc](xenonc/README.md) A CLI for Xenon written in Go
* [Swagger](swagger-adapter/README.md) Automatically generate Swagger description of your services
* [Failsafe](xenon-failsafe/README.md) Retry and circuit breaking for xenon operations

## Releases and Major Branches
Every project follows different release cycle. Consult every project's README file for details.

## Contributing

The xenon-utils project team welcomes contributions from the community. If you wish to contribute code and you have not
signed our contributor license agreement (CLA), our bot will update the issue when you open a Pull Request. For any
questions about the CLA process, please refer to our [FAQ](https://cla.vmware.com/faq). For more detailed information,
refer to [CONTRIBUTING.md](CONTRIBUTING.md).

## License
Projects under xenon-utils are distributed under the [ASL 2.0](LICENSE.txt) license.
