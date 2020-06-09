
all: clean

clean: 
	find . -name 'control_panel' -type f -exec rm -f {} \; 
	go clean

go-get:
	rm -rf src/github.com
	go get -v github.com/gorilla/mux
	go get -v github.com/streadway/amqp

run:
	go run src/control_panel/$(app).go

main:
	go run src/control_panel/main.go

install:
	go install control_panel

build:
	go build control_panel

start:
	./control_panel

test-ping:
	curl -X GET localhost:10000

docker-build: 
	docker build -t krutikavk/bitly_cp .

docker-push:
	docker push krutikavk/bitly_cp

docker-run:
	docker run --name bitly_cp -td -p 10000:10000 krutikavk/bitly_cp
	docker ps

docker-network:
	docker network inspect host

docker-shell:
	docker exec -it bitly_cp bash 

docker-clean:
	docker stop bitly_cp
	docker rm bitly_cp
	docker rmi bitly_cp

docker-ip:
	docker-machine ip

