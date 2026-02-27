# Load tests

## Prerequisites
The application must be running and accessible for the load tests in order to run sensibly. You can use the `docker-compose-gatling.yml` to do so:

```shell
mvn clean verify -DskipTests
docker compose -f docker-compose-gatling.yml build --no-cache --force-rm
docker compose -f docker-compose-gatling.yml up -d --force-recreate
```
This will run the application with the `gatling` profile which loads dummy JMS consumers for `APNS` and `Firebase`. This means that JMS messages will be produced and consumed but not actually sent to `APNS` or `Firebase`.

## Run
There is a maven property `skipLoadtests` which defaults to `true`. Set it to `false` if load tests are supposed to run and use maven to do so:

```shell
mvn clean verify -DskipLoadtests=false
```

## Results
Find an HTML report afterward in `target/gatling/index.html`.
