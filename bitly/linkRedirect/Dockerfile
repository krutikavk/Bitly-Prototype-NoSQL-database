FROM golang:latest
EXPOSE 9998
RUN mkdir /app
ADD . /app/
WORKDIR /app
ENV GOPATH /app
RUN cd /app ; go install link_redirect 
CMD ["/app/bin/link_redirect"]
