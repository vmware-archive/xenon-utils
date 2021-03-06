# xenonc

Simple command line interface to Xenon services.

## Installation

Go 1.7 is required to build xenonc. The easiest way to is to `go get` it:

```
go get github.com/vmware/xenon-utils/xenonc
xenonc -v
```

Developers may wish to clone and build it using make
```
git clone git@github.com:vmware/xenon-utils.git
cd xenon-utils/xenonc
make
```

## Usage

`xenonc` takes an HTTP verb argument, a service location, and a list of
flags that build a JSON request body.

The `XENON` environment variable must point to the Xenon node you want to talk to.

For example:

```
export XENON=http://localhost:8000/
```

As XENON services typically respond with JSON, we recommend using a tool such as
[jq][1] to interpret and transform these responses.

[1]: http://stedolan.github.io/jq/


To get this particular node's management information:

```
$ xenonc get /core/management | jq -r .systemInfo.ipAddresses[0]
10.0.1.41
```

To POST to the example factory service:

```
$ xenonc post /core/examples \
    --name=Joe \
    --keyValues.keyA=valueA \
    --keyValues.keyB=valueB
{
  "keyValues": {
    "keyA": "valueA",
    "keyB": "valueB"
  },
  "name": "Joe",
}
```

### Flags

* Use a verbatim `key` to specify a property in an object
* Use a dot to nest properties, e.g. `keyA.keyB`
* Use brackets to index into an array property, e.g. `array[2]`

Combine these to build complex objects.

For example:

* `--key.array[0].foo=bar`
* `--key.array[1].qux=foo`
