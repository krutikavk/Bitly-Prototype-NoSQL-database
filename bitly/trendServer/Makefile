
all: clean

clean: 
	find . -name 'trend_server' -type f -exec rm -f {} \; 
	go clean

go-get:
	rm -rf src/github.com
	go get -v github.com/gorilla/mux
	go get -v github.com/streadway/amqp

run:
	go run src/trend_server/$(app).go

main:
	go run src/trend_server/ts_main.go

install:
	go install trend_server

build:
	go build trend_server

start:
	./trend_server

test-ping:
	curl -X GET localhost:10000

docker-build: 
	docker build -t krutikavk/bitly_ts .

docker-push:
	docker push krutikavk/bitly_ts

docker-run:
	docker run --name bitly_ts -td -p 10000:10000 krutikavk/bitly_ts
	docker ps

docker-network:
	docker network inspect host

docker-shell:
	docker exec -it bitly_ts bash 

docker-clean:
	docker stop bitly_ts
	docker rm bitly_ts
	docker rmi bitly_ts

docker-ip:
	docker-machine ip

