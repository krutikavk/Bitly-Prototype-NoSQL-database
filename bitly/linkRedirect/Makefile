
all: clean

clean: 
	find . -name 'link_redirect' -type f -exec rm -f {} \; 
	go clean

go-get:
	rm -rf src/github.com
	go get -v github.com/gorilla/mux
	go get -v github.com/streadway/amqp

run:
	go run src/link_redirect/$(app).go

main:
	go run src/link_redirect/lr_main.go

install:
	go install link_redirect

build:
	go build link_redirect

start:
	./link_redirect

test-ping:
	curl -X GET localhost:10000

docker-build: 
	docker build -t krutikavk/bitly_lr .

docker-push:
	docker push krutikavk/bitly_lr

docker-run:
	docker run --name bitly_lr -td -p 10000:10000 krutikavk/bitly_lr
	docker ps

docker-network:
	docker network inspect host

docker-shell:
	docker exec -it bitly_lr bash 

docker-clean:
	docker stop bitly_lr
	docker rm bitly_lr
	docker rmi bitly_lr

docker-ip:
	docker-machine ip

