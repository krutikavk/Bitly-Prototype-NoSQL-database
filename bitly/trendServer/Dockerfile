FROM golang:latest
EXPOSE 10000
RUN mkdir /app
ADD . /app/
WORKDIR /app
ENV GOPATH /app
RUN cd /app ; go install trend_server 
CMD ["/app/bin/trend_server"]
