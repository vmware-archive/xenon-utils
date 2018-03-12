Test service that can proxy using xenon-failsafe, useful for demonstrating the failsafe features

# proxy a GET request
curl -X POST -H "Content-type: application/json" -d '{"url":"http://localhost:8000/slow?delay=30000"}' http://localhost:8000/proxy

# a delay service that sleeps an amount of milliseconds

curl http://localhost:8000/slow?delay=1000

# a failure service that always fails with a configurable message

curl http://localhost:8000/fail?message=expected+error
