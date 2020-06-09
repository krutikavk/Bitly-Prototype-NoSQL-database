package main

import (
	"log"
  	"fmt"
    "github.com/streadway/amqp"
  	"strings"
  	"net/http"
	"io/ioutil"
	"bytes"
	"database/sql"
	"sync"
	"encoding/json"
    "strconv"
	_ "github.com/go-sql-driver/mysql"
)



//Channel reads information received from RabbitMQ channel for creating new shortlinks
var newSLRMQ = make(chan string)

//Channel reads information received from RabbitMQ channel for used shortlinks
var usedSLRMQ = make(chan string)

var rpcSendTrends  = make(chan string)
var rpcRecvTrends = make(chan string)

var rpcSendGetAll  = make(chan string)
var rpcRecvGetAll = make(chan string)


var dbMux sync.Mutex

func failOnError(err error, msg string) {
  if err != nil {
    log.Fatalf("%s: %s", msg, err)
  }
}

//Subscribe to new_shortlinks rabbitMQ exchange
func subscribeNewSLRabbitMQ() {

    //DEPLOYMENT
	conn, err := amqp.Dial("amqp://guest:guest@10.0.2.228:5672/")

    //LOCAL
    //conn, err := amqp.Dial("amqp://guest:guest@localhost:5672/")


	failOnError(err, "TS Failed to connect to RabbitMQ")
	defer conn.Close()

	//Channel for handling queuing on RabbitMQ
	ch, err := conn.Channel()
	failOnError(err, "TS new_shortlink Failed to open a channel")
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


    failOnError(err, "TS new_shortlink Failed to declare fanout exchange")

    q, err := ch.QueueDeclare(
                "",    // name
                false, // durable
                false, // delete when unused
                true,  // exclusive
                false, // no-wait
                nil,   // arguments
        )
    failOnError(err, "TS new_shortlink Failed to declare a queue")

    err = ch.QueueBind(
                q.Name, // queue name
                "",     // routing key
                "new_shortlink", // exchange
                false,
                nil,
        )
    failOnError(err, "TS new_shortlink Failed to bind a queue")

    msg, err := ch.Consume(
                q.Name, // queue
                "",     // consumer
                true,   // auto-ack
                false,  // exclusive
                false,  // no-local
                false,  // no-wait
                nil,    // args
        )
    failOnError(err, "TS new_shortlink Failed to subscribe to queue--Trend Server")

    //fmt.Println("Successful")

    for message := range msg {
    	//send on a channel
    	fmt.Println("New shortlink created " , message)
    	newSLRMQ <- string(message.Body)
    }
}

//Process new shortlink message received
func processNewSLFromRMQ() {
	for message := range newSLRMQ {

		//New messages need to be sent to nosql db as a POST message

        //DEPLOYMENT
		url := "http://project-nosql-private-f68cfa636a5eff5f.elb.us-west-2.amazonaws.com/api/"

        //LOCAL
        //url := "http://localhost:9090/api/"

		split := strings.Split(message, "|")
		fmt.Println("Split " , split)
		fmt.Println("split[0] ", split[0])
		fmt.Println(len(split))

		//POST|sl|URL
		//URL: http://localhost:9090/api/c29tZXRoaW5nZWxzZQ== 
		//curl to db/sl with sl:URL 

		if split[0] == "POST" && len(split) == 3{


			//Once trend server receives a POST message on new_shortlinks, it will add the information 
			//to nosql database that LR will later access as a cache
			url = url + split[1]
		    fmt.Println("URL:>", url)

		    reqBody, err := json.Marshal(map[string]string {"redirectURL" : split[2]})
		    
		    if err != nil {
            	log.Fatalln(err)
        	} 

		    resp, err := http.Post(url, "application/json", bytes.NewBuffer(reqBody))
            body, err := ioutil.ReadAll(resp.Body)
            if err != nil {
                log.Fatalln(err)
            }
 
		    
		    fmt.Println("response Status:", resp.Status)
		    fmt.Println("response Body:", string(body))
		    resp.Body.Close()

		    //End adding to nosql DB


		    //It will also add the information to main mysql trendDB
		    dbMux.Lock()
		    fmt.Println("Adding new shortlink to Trends DB")
	        db, err := sql.Open("mysql", "admin:bitlyadmin@tcp(bitly.czetep2ih4kd.us-west-2.rds.amazonaws.com:3306)/trendDB")

	        if err != nil {
	            panic(err.Error())
	        }
	        err = db.Ping()
	        if err != nil {
	            panic(err.Error())
	        }

	        fmt.Println("TS process NewSL Successfully connected to Trends DB")
	        split := strings.Split(message, "|")
	        fmt.Println(split[0], split[1], split[2])
	        switch (split[0]) {
	            case "POST"  : 
	            query, err := db.Prepare("Insert into linkTrends values (?, ?, 0)")

	            if err != nil {
	                panic(err.Error())
	            }
	            query.Exec(split[1], split[2])
	            fmt.Println("TS process NewSL Successfully wrote Trends DB")
	            query.Close()
	        }    
	        db.Close()
	        dbMux.Unlock()


		} else {
			fmt.Println("processNewSLFromRMQ: Message received is not POST")
		}
	}
}


func subscribeUsedSLRabbitMQ() {

    //DEPLOYMENT
	conn, err := amqp.Dial("amqp://guest:guest@10.0.2.228:5672/")

    //LOCAL
    //conn, err := amqp.Dial("amqp://guest:guest@localhost:5672/")

	failOnError(err, "TS Failed to connect to RabbitMQ")
	defer conn.Close()

	//Channel for handling queuing on RabbitMQ
	ch, err := conn.Channel()
	failOnError(err, "TS new_shortlink Failed to open a channel")
	defer ch.Close()

	err = ch.ExchangeDeclare(
                "used_shortlink",   // name
                "fanout", // type
                true,     // durable
                false,    // auto-deleted
                false,    // internal
                false,    // no-wait
                nil,      // arguments
        )


    failOnError(err, "TS used_shortlink Failed to declare fanout exchange")

    q, err := ch.QueueDeclare(
                "",    // name
                false, // durable
                false, // delete when unused
                true,  // exclusive
                false, // no-wait
                nil,   // arguments
        )
    failOnError(err, "TS used_shortlink Failed to declare a queue")

    err = ch.QueueBind(
                q.Name, // queue name
                "",     // routing key
                "used_shortlink", // exchange
                false,
                nil,
        )
    failOnError(err, "TS used_shortlink Failed to bind a queue")

    msg, err := ch.Consume(
                q.Name, // queue
                "",     // consumer
                true,   // auto-ack
                false,  // exclusive
                false,  // no-local
                false,  // no-wait
                nil,    // args
        )
    failOnError(err, "TS used_shortlink Failed to subscribe to queue--Trend Server")

    for message := range msg {

    	fmt.Println("Used shortlink " , string(message.Body))
    	usedSLRMQ <- string(message.Body)
    }
}

func processUsedSLFromRMQ() {

	for message := range usedSLRMQ {
		//Post to trend mysql db

		dbMux.Lock()
        db, err := sql.Open("mysql", "admin:bitlyadmin@tcp(bitly.czetep2ih4kd.us-west-2.rds.amazonaws.com:3306)/trendDB")

        if err != nil {
            panic(err.Error())
        }
        err = db.Ping()
        if err != nil {
            panic(err.Error())
        }

        fmt.Println("TS process UsedSL Successfully connected to Trends DB")

        //Message is in the form of GET|redirectKey|URL, interested only in redirect key
        split := strings.Split(message, "|")
        fmt.Println(split[0], split[1], split[2])
        switch (split[0]) {
            case "GET"  : 
            query, err := db.Prepare("Update linkTrends set hits = hits + 1 where shortlink = ?")

            if err != nil {
                panic(err.Error())
            }
            fmt.Println("TS process UsedSL Successfully updated hits on Trends DB")
            query.Exec(split[1])
            query.Close()
        }    
        db.Close()
        dbMux.Unlock()
	}
}


func handleRPCTrends() {

    //LOCAL    
    //conn, err := amqp.Dial("amqp://guest:guest@localhost:5672/")

    //DEPLOYMENT
    conn, err := amqp.Dial("amqp://guest:guest@10.0.2.228:5672/") 

    failOnError(err, "Failed to connect to RabbitMQ")
    defer conn.Close()

    ch, err := conn.Channel()
    failOnError(err, "Failed to open a channel")
    defer ch.Close()

    q, err := ch.QueueDeclare(
            "rpc_queue_trends", // name
            false,       // durable
            false,       // delete when unused
            false,       // exclusive
            false,       // no-wait
            nil,         // arguments
    )
    failOnError(err, "Failed to declare a queue")

    err = ch.Qos(
            1,     // prefetch count
            0,     // prefetch size
            false, // global
    )
    failOnError(err, "Failed to set QoS")

    msgs, err := ch.Consume(
            q.Name, // queue
            "",     // consumer
            false,  // auto-ack
            false,  // exclusive
            false,  // no-local
            false,  // no-wait
            nil,    // args
    )
    failOnError(err, "Failed to register a consumer")

    for d := range msgs {
        rpcSendTrends <- string(d.Body) //incoming msg is text/plain, convert it to string first
        rpc_resp := <- rpcRecvTrends

        //convert response to json
        // {{"key" : "xyz"} {"key" : "pqrs"}} ...

        err = ch.Publish(
                    "",        // exchange
                    d.ReplyTo, // routing key
                    false,     // mandatory
                    false,     // immediate
                    amqp.Publishing{
                        ContentType:   "text/plain",
                        CorrelationId: d.CorrelationId,
                        Body:          []byte(rpc_resp),
                    })
                    failOnError(err, "Failed to publish a message")

                    d.Ack(false)

    }

}

func processRPCTrends() {
    for m := range rpcSendTrends {
        split := strings.Split(m, "|")

        //Incoming message: TRENDS|dummy
        switch (split[0]) {
            case "TRENDS"  :

                //Read 10 most popular links from trends database
                dbMux.Lock()
                db, err := sql.Open("mysql", "admin:bitlyadmin@tcp(bitly.czetep2ih4kd.us-west-2.rds.amazonaws.com:3306)/trendDB")

                if err != nil {
                    panic(err.Error())
                }
                err = db.Ping()
                if err != nil {
                    panic(err.Error())
                }

                fmt.Println("TS process UsedSL Successfully connected to Trends DB")

                var q_res string;
                
                rows, err := db.Query("select * from linkTrends order by hits desc limit 10")

                if err != nil {
                    panic(err.Error())
                }

                //Iterate through rows returned by mysql
                for rows.Next() {
                    var sl_key string
                    var sl_val string
                    var t_hits int
                    err := rows.Scan(&sl_key, &sl_val, &t_hits)
                    if err != nil {
                        log.Fatal(err)
                    }
                    //add strconv to imports
                    q_res += "TRENDS|"+sl_key+"|"+sl_val+"|"+ strconv.Itoa(t_hits)+"|"
                    log.Println(sl_key, sl_val, t_hits)
                }
                rows.Close()

                fmt.Println("TS process processRPCTrends")
                
                err = rows.Err()
                if err != nil {
                    log.Fatal(err)
                }
                fmt.Println("TS Sending ", q_res , " back to CP");

                db.Close()
                dbMux.Unlock()

                rpcRecvTrends <- q_res   
                
        }

    }


}

func handleRPCGetAll() {

    //LOCAL    
    //conn, err := amqp.Dial("amqp://guest:guest@localhost:5672/")

    //DEPLOYMENT
    conn, err := amqp.Dial("amqp://guest:guest@10.0.2.228:5672/") 

    failOnError(err, "Failed to connect to RabbitMQ")
    defer conn.Close()

    ch, err := conn.Channel()
    failOnError(err, "Failed to open a channel")
    defer ch.Close()

    q, err := ch.QueueDeclare(
            "rpc_queue_getall", // name
            false,       // durable
            false,       // delete when unused
            false,       // exclusive
            false,       // no-wait
            nil,         // arguments
    )
    failOnError(err, "Failed to declare a queue")

    err = ch.Qos(
            1,     // prefetch count
            0,     // prefetch size
            false, // global
    )
    failOnError(err, "Failed to set QoS")

    msgs, err := ch.Consume(
            q.Name, // queue
            "",     // consumer
            false,  // auto-ack
            false,  // exclusive
            false,  // no-local
            false,  // no-wait
            nil,    // args
    )
    failOnError(err, "Failed to register a consumer")

    for d := range msgs {
        rpcSendGetAll <- string(d.Body) //incoming msg is text/plain, convert it to string first
        rpc_resp := <- rpcRecvGetAll

        //convert response to json
        // {{"key" : "xyz"} {"key" : "pqrs"}} ...

        err = ch.Publish(
                    "",        // exchange
                    d.ReplyTo, // routing key
                    false,     // mandatory
                    false,     // immediate
                    amqp.Publishing{
                        ContentType:   "text/plain",
                        CorrelationId: d.CorrelationId,
                        Body:          []byte(rpc_resp),
                    })
                    failOnError(err, "Failed to publish a message")

                    d.Ack(false)

    }

}


func processRPCGetAll() {
    for m := range rpcSendGetAll {
        split := strings.Split(m, "|")

        //Incoming message is in the form LINKS|dummy
        switch (split[0]) {
            case "LINKS"  :

                //Return all shortlinks from created from main/trends database
                dbMux.Lock()
                db, err := sql.Open("mysql", "admin:bitlyadmin@tcp(bitly.czetep2ih4kd.us-west-2.rds.amazonaws.com:3306)/trendDB")

                if err != nil {
                    panic(err.Error())
                }
                err = db.Ping()
                if err != nil {
                    panic(err.Error())
                }

                fmt.Println("TS process UsedSL Successfully connected to Trends DB")

                var q_res string;
                
                rows, err := db.Query("select * from linkTrends")

                // if there is an error inserting, handle it
                if err != nil {
                    panic(err.Error())
                }

                for rows.Next() {
                    var sl_key string
                    var sl_val string
                    var t_hits int
                    err := rows.Scan(&sl_key, &sl_val, &t_hits)
                    if err != nil {
                        log.Fatal(err)
                    }

                    q_res += "LINKS|"+sl_key+"|"+sl_val+"|"+ strconv.Itoa(t_hits)+"|"
                    log.Println(sl_key, sl_val, t_hits)
                }
                rows.Close()

                fmt.Println("TS process UsedSL Successfully updated hits on Trends DB")
                
                err = rows.Err()
                if err != nil {
                    log.Fatal(err)
                }
                fmt.Println("TS Sending ", q_res , " back to CP");

                db.Close()
                dbMux.Unlock()

                rpcRecvGetAll <- q_res
                   
                
        }

    }


}


func main() {

    fmt.Println("Trend Server started....")
	go subscribeNewSLRabbitMQ()
	go processNewSLFromRMQ()

	go subscribeUsedSLRabbitMQ()
	go processUsedSLFromRMQ()

    go handleRPCTrends()
    go processRPCTrends()

    go handleRPCGetAll()
    go processRPCGetAll()

	forever := make(chan bool)
	fmt.Println(" [*] Waiting for messages. To exit press CTRL+C")
    <-forever

}