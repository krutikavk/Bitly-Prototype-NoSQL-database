FROM golang:latest
EXPOSE 10000
RUN mkdir /app
ADD . /app/
WORKDIR /app
ENV GOPATH /app
RUN cd /app ; go install control_panel ; touch /app/run.log
CMD ["/app/bin/control_panel"] 
