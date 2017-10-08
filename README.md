# sample-akka-http-docker

An Sample application [Scala](scala-lang.org) that leverages  and [Akka](akka.io) 
[sbt-native-packager](https://github.com/sbt/sbt-native-packager) which is built using [Spotify docker client integration]) libs.

An Sample application that leverages [Akka-http](akka.io) and [Spray](spray.io) to expose Rest API. 
Description is exposed as Swagger Json page.
 
 
### Running
Clone to your computer and run via sbt:

```
$ sbt run
```

Generates a directory with the Dockerfile and environment prepared for creating a Docker image.
```
$ sbt docker:stage
```

Builds an image using the local Docker server.
```
$ sbt docker:publishLocal
```

Builds an image using the local Docker server, and pushes it to the configured remote repository.
```
$ sbt docker:publish
```

Removes the built image from the local Docker server.
```
$ sbt docker:clean
```

To run image and bind to host MongoDB port (27017) and Http interface :

```
$ docker run \
        --name newsbridge-sample-akka-http-docker \
        -p 8080:8080 \
        newsbridge/sample-akka-http-docker:latest
```

Test with httpie
```
 http localhost:8080/hello/flore
```


## Option push to ECS
```
sbt clean compile docker:publishLocal
$(aws ecr get-login --no-include-email --region eu-west-1)  
docker tag sample-akka-http-docker:latest 677537359471.dkr.ecr.eu-west-1.amazonaws.com/sample-akka-http-docker:latest 
docker push 677537359471.dkr.ecr.eu-west-1.amazonaws.com/sample-akka-http-docker:latest
```

## Option create docker instance locally
```
$(aws ecr get-login --no-include-email --region eu-west-1)
docker run --name sample-akka-http-docker -p 8080:8080 677537359471.dkr.ecr.eu-west-1.amazonaws.com/sample-akka-http-docker:latest
curl http://0.0.0.0:8080/hello/fred
```

## Release
```
$(aws ecr get-login --no-include-email --region eu-west-1)
sbt release
```

### License
This library is licensed under the Apache License, Version 2.0.


