var express=require("express"); 
var bodyParser=require("body-parser");
var request = require("request");
//if package name is in double quotes, node will search in node_modules in local dir : https://www.bennadel.com/blog/2169-where-does-node-js-and-require-look-for-modules.htm
var app=express();
//setup express to use ejs as templating engine :https://www.geeksforgeeks.org/use-ejs-as-template-engine-in-node-js/
app.set('view engine', "ejs");
app.use(express.static(__dirname));
app.use(bodyParser.json()); 
app.use(express.static('public')); 
app.use(bodyParser.urlencoded({ 
    extended: true
}));

app.post('/post', function(req,res){ 
    var name = req.body.page_name; 
    console.log('Inside post'); 
    console.log(name); 
    var options = {
        //CP API gateway endpoint
        uri: 'https://i98p7dgzzb.execute-api.us-west-2.amazonaws.com/prod-cp/post',
        //uri: 'http://localhost:10000/post',
        method: 'POST',
        headers: {'content-type' : 'application/json'},
        json: {
            "key"    : "",
            "value"  : name
        }
    };
    request(options, function (error, response, body) {
        if (!error && response.statusCode == 200) {
            console.log(body)
            res.render('display_sl', {'incomingData' :body});
        }
    });
    //return res.redirect('signup_success.html'); 
})

app.get('/trending', function(req, res){
    console.log('Inside get trending');
    var options = {
        //uri: 'http://localhost:10000/trending',
        uri: 'https://i98p7dgzzb.execute-api.us-west-2.amazonaws.com/prod-cp/trending',
        method: 'GET',
        headers: {'content-type' : 'application/json'},
        json: {
            "key"    : "",
            "value"  : "",
            "hits"   : ""
        }
    };
    request(options, function (error, response, body) {
        if (!error && response.statusCode == 200) {
            console.log(body)
            res.render('display_trending', {'incomingData' :body});
        }
    });
})


app.get('/getall', function(req, res){
    console.log('Inside get all');
    var options = {
        //uri: 'http://localhost:10000/getall',

        uri: 'https://i98p7dgzzb.execute-api.us-west-2.amazonaws.com/prod-cp/getall',
        method: 'GET',
        headers: {'content-type' : 'application/json'},
        json: {
            "key"    : "",
            "value"  : ""
        }
    };
    request(options, function (error, response, body) {
        if (!error && response.statusCode == 200) {
            console.log(body)
            res.render('display_getall', {'incomingData' :body});
        }
    });
})


app.get('/',function(req,res){ 
    res.set({ 
        'Access-control-Allow-Origin': '*'
    }); 
    return res.redirect('new_shortlink.html'); 
}).listen(process.env.PORT || 8082) 
  
  
console.log("server listening at port 8082"); 


