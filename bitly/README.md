
# BITLY CLONE

Components: Control Panel, Link Redirect, Trend Server, RabbitMQ, NoSQL cluster, RDS
CP, LR, TS all are Go Rest APIs.

## Message Qeuing Service: RabbitMQ
RabbitMQ is the central backbone of the system over which control panel, link redirect and trend servers communicate in a publish-subscribe model.
There are 2 exchanges on RabbitMQ running on an Amazon EC2 instance
1. new_shortlink </br>
   Publisher: Control Panel</br>
   Subscriber: Trend Server</br>
   </br>
   As soon as a new shortlink is requested by a user, control panel generates a hashed base62-encoded shortlink and publishes   it to RabbitMQ. 
   Trend server consumes this message, processes it and saves it to the Main database inside AWS RDS. It also generates a POST request to the NoSQL cluster to saves the new link. This information will later be used by Link redirect server upon a redirection request.
   
2. used_shortlink</br>
   Publisher: Link Redirect</br>
   Subscriber: Trend Server</br>
   
   When a user attempts to use the new redirection link provided by Control panel server, link redirect will publish a message on RabbitMQ on this exchange. Trend server will consume this message and store the updated hits on the shortlink in the Main database which is a trends database too.
   
## Control Panel Servers
Control Panel server exposes REST methods for the frontend for a user to generate a new short-link, view trending shortlinks, and to view all created shortlinks. Control panel module is autoscaled and load balanced between 1-3 instances based on CPU utilization.
API endpoints for each:
1. /ping: Home page
2. /post: Request a new short link
3. /trending: Get trending shortlinks (top 10)
4. /getall: Get all short links created


## Link Redirect Servers
Link Redirect server exposes REST methods for the frontend to redirect to required website using newly created or existing shortlinks. Link redirect module is autoscaled and load balanced between 1-3 instances based on CPU utilization.
API Endpoints exposed:
1. /ping


## Trend Server
Trend server interfaces with the main database and the NoSQL database cluster. It will write to main database and NoSQL cluster when new short links are created. When a redirect is requested to link redirect, trend server will also update trends for the link inside the database.
No APIs are exposed for trend server.



## 5-Node NoSQL Cluster
This is a deployment of 5-node AP NoSQL database deisgned for individual project. Only trend server writes to the database, while both trend server and link redirect server can write to it.


## AWS RDS as Main and Trends Database
This serves as a main database strong a mapping of original URL to shortlinks generated as well as the number of hits for each link. Only trend server can write to the database and writes are protected by a mux.


## Deployment
1. Control Panel servers are auto scaled and load balanced with an internal Network ELB. 
2. Link Redirect Servers are auto scaled and load balanced with an internal Network ELB.

## Challenges in implementation
1. Designing an architecture for the system based on the input constraints required was a great exercise in application of teh different service architectures discussed in beginning of the class. This service resembles a 4-service microservice architecture. 
2. Designing and implementing a CQRS messaging system for the backend servers to communicate on was a good challenge. It involved researching on RabbitMQ messaging models like work queues, simple queues, publish-subscribe. I selected the publish-subscribe as it seemed a good match for communication in the multi-layered system. I have also implemented a Remote procedure call using RabbitMQ when Control Panel API requests link trends.
3. Concurrency programming in Golang initially looked a little different from other languages I have used, but turned out to be super elegant and simple to implement with the next module.

## Future Improvements
This system can be easily scaled for more number of users based on its simple design architecture. The UI experience can be made better by bringing the front-facing servers closer to the users based on their location. The databases can also be scaled for redundancy.

