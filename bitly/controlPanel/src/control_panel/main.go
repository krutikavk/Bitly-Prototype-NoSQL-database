package main

import (

  	"log"
  	"fmt"
	"net/http"
    "encoding/json"
    "github.com/gorilla/mux"
    "strings"
    "math/rand"
    "time"
    "hash/fnv"
    "github.com/catinello/base62"
    "github.com/streadway/amqp"
  
)

type KVPair struct{
	Key string 
	Value string
}

type trend struct{
    Key string
    Value string
    Hits string
}

//Intermediate channel that rabbitMQ exchange reads from to queue in its own queue
var sendRMQ = make(chan string)

//Used for communicating between CRUD handlers and packageMessage to create a message for rabbitMQ
//Format is CRUD|key|value CRUD|<redirect-key>|URL
var processForRMQ = make(chan string)
var feedbackforRMQ = make(chan string)



func failOnError(err error, msg string) {
  if err != nil {
    log.Fatalf("%s: %s", msg, err)
  }
}

func hash(s string) uint32 {
    h := fnv.New32a()
    h.Write([]byte(s))
    return h.Sum32()
}


func randomString(l int) string {
        bytes := make([]byte, l)
        for i := 0; i < l; i++ {
                bytes[i] = byte(randInt(65, 90))
        }
        return string(bytes)
}

func randInt(min int, max int) int {
        return min + rand.Intn(max-min)
}


//Thread to publish to rabbitMQ queue processing
func publishRabbitMQ() {

	//Connect to RabbitMQ server

    //Connecting everything in public subnet
	//conn, err := amqp.Dial("amqp://guest:guest@ec2-18-236-177-26.us-west-2.compute.amazonaws.com:5672/")
    //Connecting in private_2_jumpbox subnet

    //DEPLOYMENT
    conn, err := amqp.Dial("amqp://guest:guest@10.0.2.228:5672/")

    //LOCAL
    //conn, err := amqp.Dial("amqp://guest:guest@localhost:5672/")
    
	failOnError(err, "CP Failed to connect to RabbitMQ")
	defer conn.Close()

	//Channel for handling queuing on RabbitMQ
	ch, err := conn.Channel()
	failOnError(err, "Failed to open a channel")
	defer ch.Close()

	err = ch.ExchangeDeclare(
                "new_shortlink",   // name
                "fanout", // type
                true,     // durable
                false,    // auto-deleted
                false,    // internal
                false,    // no-wait
                nil,      // arguments
        )


    failOnError(err, "CP Failed to declare fanout exchange ")

    //Process messages on input queue for RMQ
    
    for msg := range sendRMQ {
    	fmt.Println("Received ", msg, "from sendRMQ queue" )
    	err = ch.Publish(
                "new_shortlink", // exchange
                "",     // routing key
                false,  // mandatory
                false,  // immediate
                amqp.Publishing{
                        ContentType: "text/plain",
                        Body:        []byte(msg),
                })

        failOnError(err, "CP Failed to publish to RMQ")
        fmt.Println("CP New Shortlink Sent to RMQ [x] ", msg)
    }
}

func getPair(w http.ResponseWriter, r *http.Request) {
	fmt.Println("Endpoint Hit: createNewPair")
}

func createNewPair(w http.ResponseWriter, r *http.Request) {
	fmt.Println("Endpoint Hit: createNewPair")

    var kv KVPair
    err := json.NewDecoder(r.Body).Decode(&kv)
    fmt.Println(err)
    if err != nil {
        http.Error(w, err.Error(), http.StatusBadRequest)
        return
    }

    fmt.Println("key: " , kv.Key, " value: ", kv.Value)

    redirectKey := base62.Encode(int(hash(kv.Value)))

    kv.Key = redirectKey
    fmt.Println("redirect key: ", kv.Key)

    //processForRMQ <- "POST|" + redirectKey + "|" + kv.Value
    sendRMQ <- "POST|" + redirectKey + "|" + kv.Value
    fmt.Printf("%T\n", processForRMQ)
    fmt.Println("sending processForRMQ ", processForRMQ)
    json.NewEncoder(w).Encode(kv)
}

//Gets trends from the Trend Server
func getTrends(w  http.ResponseWriter, r *http.Request) {

    var trendResponse []trend
    var temp trend
    rpcResponse := processRPCTrends("TRENDS|dummy")
    split := strings.Split(rpcResponse, "|")

    if len(split)%4 != 1 || len(split) == 0 {
        http.Error(w, "Error fetching Trends", http.StatusBadRequest)
        return
    }

    for i := 0; i < len(split); i += 4 {
        if split[i] == "TRENDS" {
            temp.Key = split[i+1]
            temp.Value = split[i+2]
            temp.Hits = split[i+3]
            trendResponse = append(trendResponse, temp)
        }
    }

    b, errn := json.Marshal(trendResponse)

    if errn != nil {
        fmt.Fprintf(w, "Error Marshaling trend response")
        return
    }

    fmt.Fprintf(w, string(b))
    return

}

func processRPCTrends(req string) string{

    //DEPLOYMENT
    conn, err := amqp.Dial("amqp://guest:guest@10.0.2.228:5672/")  

    //LOCAL
    //conn, err := amqp.Dial("amqp://guest:guest@localhost:5672/")

    failOnError(err, "Failed to connect to RabbitMQ")
    defer conn.Close()

    ch, err := conn.Channel()
    failOnError(err, "Failed to open a channel")
    defer ch.Close()

    q, err := ch.QueueDeclare(
        "",    // name
        false, // durable
        false, // delete when unused
        true,  // exclusive
        false, // noWait
        nil,   // arguments
    )
    failOnError(err, "Failed to declare a queue")

    msgs, err := ch.Consume(
        q.Name, // queue
        "",     // consumer
        true,   // auto-ack
        false,  // exclusive
        false,  // no-local
        false,  // no-wait
        nil,    // args
    )
    failOnError(err, "Failed to register a consumer")

    corrId := randomString(32)

    err = ch.Publish(
        "",          // exchange
        "rpc_queue_trends", // routing key
        false,       // mandatory
        false,       // immediate
        amqp.Publishing{
            ContentType:   "text/plain",
            CorrelationId: corrId,
            ReplyTo:       q.Name,
            Body:          []byte(req),
        })
    failOnError(err, "Failed to publish a message")
    var ret_resp string
    for d := range msgs {
        if corrId == d.CorrelationId {
            ret_resp = string(d.Body)
            fmt.Println("Return response from TS: ", ret_resp);
            break
        }
    }

    return ret_resp
}


func getAllLinks(w  http.ResponseWriter, r *http.Request) {

    var links []KVPair
    var temp KVPair
    rpcResponse := processRPCGetAllLinks("LINKS|dummy")
    split := strings.Split(rpcResponse, "|")

    if len(split)%4 != 1 || len(split) == 0 {
        http.Error(w, "Error fetching Trends", http.StatusBadRequest)
        return
    }

    for i := 0; i < len(split); i += 4  {
        //split[i] should be LINKS
        if split[i] == "LINKS" {
            temp.Key = split[i+1]
            temp.Value = split[i+2]

            //Not reading split[i+3] which is number of hits
            links = append(links, temp)
        } 
    }

    b, errn := json.Marshal(links)

    if errn != nil {
        fmt.Fprintf(w, "Error Marshaling get all links")
        return
    }

    fmt.Fprintf(w, string(b))
    return

}

func processRPCGetAllLinks(req string) string{

    //DEPLOYMENT
    conn, err := amqp.Dial("amqp://guest:guest@10.0.2.228:5672/")  

    //LOCAL
    //conn, err := amqp.Dial("amqp://guest:guest@localhost:5672/")

    failOnError(err, "Failed to connect to RabbitMQ")
    defer conn.Close()

    ch, err := conn.Channel()
    failOnError(err, "Failed to open a channel")
    defer ch.Close()

    q, err := ch.QueueDeclare(
        "",    // name
        false, // durable
        false, // delete when unused
        true,  // exclusive
        false, // noWait
        nil,   // arguments
    )
    failOnError(err, "Failed to declare a queue")

    msgs, err := ch.Consume(
        q.Name, // queue
        "",     // consumer
        true,   // auto-ack
        false,  // exclusive
        false,  // no-local
        false,  // no-wait
        nil,    // args
    )
    failOnError(err, "Failed to register a consumer")

    corrId := randomString(32)

    err = ch.Publish(
        "",          // exchange
        "rpc_queue_getall", // routing key
        false,       // mandatory
        false,       // immediate
        amqp.Publishing{
            ContentType:   "text/plain",
            CorrelationId: corrId,
            ReplyTo:       q.Name,
            Body:          []byte(req),
        })
    failOnError(err, "Failed to publish a message")
    var ret_resp string
    for d := range msgs {
        if corrId == d.CorrelationId {
            ret_resp = string(d.Body)
            fmt.Println("Return response from TS: ", ret_resp);
            break
        }
    }

    return ret_resp
}


func pingTest(w http.ResponseWriter, r *http.Request){
    fmt.Fprintf(w, "Welcome to the HomePage!")
    fmt.Println("Endpoint Hit: homePage")
}


//Routing for handlers
func handleRequests() {

	myRouter := mux.NewRouter()
	myRouter.HandleFunc("/ping", pingTest).Methods("GET")
    myRouter.HandleFunc("/trending", getTrends).Methods("GET")
    myRouter.HandleFunc("/getall", getAllLinks).Methods("GET")
	myRouter.HandleFunc("/post", createNewPair).Methods("POST")
    log.Fatal(http.ListenAndServe(":10000", myRouter))
}

func main() {

    rand.Seed(time.Now().UTC().UnixNano())

	fmt.Println("Control Panel started. Listening for URL shortening requests....")
	go publishRabbitMQ()
	handleRequests()

}