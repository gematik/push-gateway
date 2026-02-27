# Push-Gateway

The Push Gateway handles the delivery of notifications from the TI context and abstracts communication with push providers, e.g. APNS or Firebase.

## License

Copyright 2021-2025 gematik GmbH

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See the [LICENSE](./LICENSE) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

## Development

### Code style
You should have the [ktfmt-Plugin](https://plugins.jetbrains.com/plugin/14912-ktfmt) installed and enabled with `google` setting to avoid code formatting issues.

In order to apply the code format run 
```shell
./mvnw spotless:apply
```

### Run

You can run the application on your machine in the following ways.

#### Docker Compose

Starting up with `docker compose` also starts a `jaeger-all-in-one` which can be reached with a browser of your choice at `http://localhost:16686`. This enables you to test tracing of the application. 

1. Create a docker network with the name `gematik-network` if it not already exists
    ```shell
    docker network create gematik-network
    ```
2. The backend should be built at least once because the build artifact is used to create the docker image. This can be done like so:
    ```shell
    ./mvnw clean package
    ```
3. Build the docker image
   ```shell
   docker compose build --no-cache --force-rm
   ```
4. Run
    ```shell
    docker compose up -d --force-recreate
    ```

#### k3d (Kubernetes)

The `charts` folder contains a `Helm Chart` which enables you to run the application in a local `kubernetes`-like environment if you have a `kubernetes cluster` up and running. There are many ways to create one, however, the usage of `k3d` is described here.

1. Install `k3d` on you machine by [following this guide](https://k3d.io/stable/#installation)
2. Create a docker registry:
   ```shell
   k3d registry create registry.localhost --port 5001
   ```
3. Create a cluster and connect it with the docker registry:
   ```shell
   k3d cluster create pgw --registry-use k3d-registry.localhost:5001
   ```
4. Create a namespace in the cluster:
   ```shell
   kubectl create namespace pgw
   ```
5. Tag the application image:
   ```shell
   docker tag push-gateway-backend:latest k3d-registry.localhost:5001/push-gateway-backend:<dockerTag>
   ```
6. Push the tagged application image into the registry:
   ```shell
   docker push k3d-registry.localhost:5001/push-gateway-backend:<dockerTag>
   ```
7. Configure the image coordinates in `$PROJECT_ROOT/charts/values/local.yaml:backend.image`
8. Create secrets for the database:
   ```shell
   kubectl create secret generic postgres-admin -n pgw \
      --from-literal=username="$(openssl rand -hex 16)" \
      --from-literal=password="$(openssl rand -hex 32)"
   ```
9. Create secrets for the broker:
   ```shell
   kubectl create secret generic broker-credentials -n pgw \
      --from-literal=username="$(openssl rand -hex 16)" \
      --from-literal=password="$(openssl rand -hex 32)"
   ```
10. Create push secrets if any, note that `--from-file=xy` is the name of the filename that must be referenced in your push configuration:
    1. APNS Certificates 
      ```shell
      kubectl create secret generic apns-pkcs12-certs -n pgw --from-file=certfile.p12=<certfile-on-your-machine>.p12
      ```
    2. APNS Tokens 
      ```shell
      kubectl create secret generic apns-pkcs8-certs -n pgw --from-file=keyfile.p8=<keyfile-on-your-machine>.p8
      ```
    3. Firebase service-account.jsons
      ```shell
      kubectl create secret generic firebase-service-account-jsons -n pgw --from-file=service-account.json=<json-file-on-your-machine>.json
      ```
Install or upgrade the application in your kubernetes cluster
   ```shell
   helm install pgw ./charts -f charts/values.yaml -f charts/values/local.yaml -n pgw
   ```
   ```shell
   helm upgrade pgw ./charts -f charts/values.yaml -f charts/values/local.yaml --install -n pgw
   ```

From now on you can use `kubectl` commands to see logs, forward ports, etc.

##### Values

Default values are maintained in `charts/values.yaml` and can be overridden by files in `charts/values/`. You can set up your own local environment by copying the default values.yaml to `charts/values/local.yaml`. This file is ignored by git and never checked in. Adapt according to your needs.   

### Debug

Running the application with `docker compose` will also open port `5005` for JVM remote debugging. However, you may also want to just run the application in your IDE in debug mode.

The application's `k3d` deployment also provides port `5005` for debugging when you have enabled debug mode in `values/local.yaml`. You can make this port available with the `kubectl port-forward` command. 

## Deployment

In order for the push gateway to be able to communicate to APNS and Firebase you need to setup credentials in your deployed environment. For APNS the Token and Certificate based methods are supported. For Android devices Firebase is supported.

The configuration of the Push Gateway can be done via `PUSH_CONFIG_FILE` environment using a configmap with configuration values (see `compose/backend/push-config.yaml.tpl` for an example).

## Application modes (APP_MODE)

The application consists of an API part and a Worker part. The API-part provides the openapi specification implementation, receives, validates and queues valid push notifications into an Artemis MQ. The Worker-part consumes the notifications from the Artemis MQ and sends them to APNS/Firebase. The application can be configured to run in either or both modes at once. It can be controlled via the `APP_MODE` environment variable (default: `BOTH`).

- `PRODUCER`: runs only the producer component (e.g., incoming requests and enqueueing).
- `CONSUMER`: runs only the consumer component (dequeueing and push delivery).
- `BOTH`: runs producer and consumer together in one process (useful for local/dev).

For scaling, deploy producer and consumer as separate workloads and scale them independently based on load. The Helm chart already applies this split by setting `APP_MODE=PRODUCER` for the producer deployment and `APP_MODE=CONSUMER` for the consumer deployment, while docker compose defaults to `BOTH`.
