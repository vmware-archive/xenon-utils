build: check test
	GOOS=linux   GOARCH=amd64 go build -o bin/xenonc-linux-amd64 .
	GOOS=darwin  GOARCH=amd64 go build -o bin/xenonc-darwin-amd64 .
	GOOS=freebsd GOARCH=amd64 go build -o bin/xenonc-freebsd-amd64 .
	GOOS=windows GOARCH=amd64 go build -o bin/xenonc-windows-amd64.exe .

test:
	@go env
	go test -v -race .

lint:
	go fmt .

check:
	@go version 2> /dev/null || echo Go 1.7 required
	@mkdir bin &> /dev/null || true

.PHONY: test
