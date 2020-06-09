package main

import (

  	"fmt"
	"net/http"
    "github.com/streadway/amqp"
    "strings"
    "io/ioutil"
    "log"
  	"encoding/json"
)

type KVPair struct {
	RedirectURL string
}

//HTTP redirect handler will input to this channel
var usedSL = make(chan string)

func failOnError(err error, msg string) {
  if err != nil {
    log.Fatalf("%s: %s", msg, err)
  }
}

func httpRedirectHandler(w http.ResponseWriter, r *http.Request) {
	key := r.URL.Path[1:]
	if len(key) == 0 {
        //LOCAL
        //http.Redirect(w, r, "http://localhost:10000/ping", 400)
        //DEPLOYMENT
        http.Redirect(w, r, "https://i98p7dgzzb.execute-api.us-west-2.amazonaws.com/prod-cp/", 400)
    } else {
    	//Fetch from nosql database, publish to used_shortlink exchange and redirect
    	//Get data from nosql database 

        //DEPLOYMENT
    	url := "http://project-nosql-private-f68cfa636a5eff5f.elb.us-west-2.amazonaws.com/api/" + key

        //LOCAL
        //url := "http://localhost:9090/api/" + key

		resp, err := http.Get(url)
		if err != nil {
			//LOCAL
            //http.Redirect(w, r, "http://localhost:10000/ping", 400)
            //DEPLOYMENT
            http.Redirect(w, r, "https://i98p7dgzzb.execute-api.us-west-2.amazonaws.com/prod-cp/", 400)
            return
		}


		//Test and handle LR
		fmt.Println("LR Read from nosql response Status:", resp.Status)
	    body, _ := ioutil.ReadAll(resp.Body)

        //Handling bad keys--redirect to home page
        if resp.Status != "200 OK" {
            //LOCAL
            //http.Redirect(w, r, "http://localhost:10000/ping", 400)
            //DEPLOYMENT
            http.Redirect(w, r, "https://i98p7dgzzb.execute-api.us-west-2.amazonaws.com/prod-cp/", 400)     
            return 
        }

	    
	    fmt.Println(string(body))
        //keys := make([]KVPair, 1)
        var result KVPair
        json.Unmarshal(body, &result)

        fmt.Println("Redirect URL " , result.RedirectURL)
        if strings.Contains(result.RedirectURL, "http") {
            http.Redirect(w,r, result.RedirectURL, 302)
        } else {
            http.Redirect(w, r, "http://" + result.RedirectURL, 302)
        }

        
        message := "GET|" + key + "|" + result.RedirectURL
        fmt.Println("Sent ", message, " to used RMQ")
	    usedSL <- message
    }
}


func publishUsedSLToRMQ() {

	//Connect to RabbitMQ server
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
                "used_shortlink",   // name
                "fanout", // type
                true,     // durable
                false,    // auto-deleted
                false,    // internal
                false,    // no-wait
                nil,      // arguments
        )


    failOnError(err, "LR Failed to declare fanout exchange ")

    //Process messages on input queue for RMQ 

    //str := <- sendRMQ
    //fmt.Println("Received ", str, "from sendRMQ queue" )
    
    for msg := range usedSL {
    	fmt.Println("Received ", msg, "from usedSL queue" )
    	err = ch.Publish(
                "used_shortlink", // exchange
                "",     // routing key
                false,  // mandatory
                false,  // immediate
                amqp.Publishing{
                        ContentType: "text/plain",
                        Body:        []byte(msg),
                })

        failOnError(err, "LR Failed to publish to RMQ")
        //fmt.Println(" shortlinks_created [x] Sent %s", msg)
        fmt.Println("LR New Shortlink Sent to RMQ [x] ", msg)
    }
}

func pingRequestHandler(w http.ResponseWriter, r *http.Request) {
    fmt.Fprintf(w, "Welcome to the Link Redirect!")
}

func main() {

    fmt.Println("Link Redirect Server started. Listening for URL redirection requests....")

	http.HandleFunc("/", httpRedirectHandler)
    http.HandleFunc("/ping", pingRequestHandler)
	go publishUsedSLToRMQ()  

	err := http.ListenAndServe(":9998", nil)
    if err != nil {
        log.Fatal("ListenAndServe: ", err)
    }

    forever := make(chan bool)
	fmt.Println(" [*] LR Waiting for messages. To exit press CTRL+C")
    <-forever

}