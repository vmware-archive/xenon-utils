# jepsen.xenon

This folder contains Jepsen tests for Xenon and Xenon Clojure client to help write Jepsen tests.

Jepsen is a framework for distributed systems verification, with fault injection, written by Kyle Kingsbury.
It fuzzes the system with random operations while injecting network partitions.
The results of operation history is analyzed to see if the system violates any of the consistency properties it claims to have.
It generates graphs of performance and availability, helping user characterize how a system responds to different faults.

We ran a Jepsen test, as part of our Xenon testing, to determine if any consistency issues could be uncovered.
In our testing of Read, Write and CAS (Compare-And-Set) operations, we found them to be linearizable,
and Xenon gracefully recovered from partitions without introducing any consistency issues.

Refer following repos for more details on Xenon and Jepsen.

 * Xenon: https://github.com/vmware/xenon
 * Jespen: https://github.com/jepsen-io/jepsen

## Requirements

 * docker
 * docker-compose

## Testing Xenon

Simply run following commands to start a cluster of five docker nodes for jepsen testing.

```
cd docker
./up.sh
```

On completion of above command you will get following message.

```
jepsen-control | Welcome to Jepsen on Docker
jepsen-control | ===========================
jepsen-control |
jepsen-control | Please run `docker exec -it jepsen-control bash` in another terminal to proceed.
```

After running `docker exec -it jepsen-control bash` on another terminal, run following command to start the tests.
This command runs the test which first installs the Xenon host on the five docker nodes and one cluster and then starts jepsen testing
on them.

```
lein run test --time-limit 60 --concurrency 10
```

## Testing Xenon Clojure Client library

Following command will run client library test to verify the xenon clojure client library.

```
lein test :only jepsen.xenonclient-test
```

## TODO

 * Add checker for eventual consistency on queries
 * Add clock related tests
 * Add tests that break the system

## Credits

Thanks to Kyle for Jepsen framework and example code that has been used in this folder. Kyle Kingsbury: https://github.com/aphyr
