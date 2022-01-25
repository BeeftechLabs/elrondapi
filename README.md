# Elrond API

An easy to deploy, fast and efficient Elrond API using Ktor server and Kotlin Coroutines.

You can test it out on our [Alpha deployment](https://api-alpha.partnerstaking.com/) (also check out the OpenAPI spec).

## Configuring

This project uses Hoplite for configuration. An example [config.yaml](https://github.com/BeeftechLabs/elrondapi/blob/main/config.yaml.example) is provided. Some parameters are optional, though functionality will be limited.

For development purposes, one can change specific configuration properties into config-dev.yaml, which will override the default ones (eg. local Redis etc).

## Packaging

There's multiple ways to deploy the project. But first, one must package it:

- Executable JVM application

  An executable JVM application is a packaged application that includes code dependencies and generated start scripts. Works great when deploying using Docker. You must use the [Gradle Application Plugin](https://ktor.io/docs/gradle-application-plugin.html) in order to build executable JVM applications. **This is already configured in the current repo.**

- Fat JAR (not configured)

  A fat JAR is an executable JAR that includes all code dependencies. You can deploy it to any cloud service that supports fat JARs. You can use the [Shadow plugin](https://ktor.io/docs/fatjar.html) to build it.

- WAR (not configured)

  A WAR archive lets you deploy your application inside a servlet container, such as Tomcat or Jetty. You need to add the [Ktor server servlet](https://ktor.io/docs/war.html#configure-ktor) artifact.

- GraalVM (not configured)

  In order to build GraalVM images, one has to build a Fat JAR first. [See documentation](https://ktor.io/docs/graalvm.html).

## Deploying

Now that you decided on a packaging method, decide on how you'll deploy it:

- [Docker](https://ktor.io/docs/docker.html)

  Works great in combination with Executable JVM applications, and it's the method PartnerStaking uses for deployment.

  1. Create a Dockerfile. An example is added to this repo.
    ```bash
    FROM openjdk:11-jdk
    EXPOSE 8080:8080
    RUN mkdir /app
    COPY ./build/install/com.beeftechlabs.elrondapi/ /app/
    COPY ./config.yaml /app/bin/
    WORKDIR /app/bin
    CMD ["./com.beeftechlabs.elrondapi"]
    ```
  2. Build the app image
    ```bash
    ./gradlew installDist
    ```
  3. Build and tag the Docker image
    ```bash
    docker build -t elrond-api .
    ```
  4. Start the image (or alternatively deploy it somewhere else)
    ```bash
    docker run -p 8080:8080 elrond-api
    ```
  
  IntelliJ IDEA can also run a Docker image by clicking the Run icon in the Dockerfile.


- [App Engine](https://ktor.io/docs/google-app-engine.html)
- [Heroku](https://ktor.io/docs/heroku.html)
- [AWS Elastic Beanstalk](https://ktor.io/docs/elastic-beanstalk.html)

## Features

- [x] Address Details
  - [x] Balance
  - [x] ESDTs, NFTs and SFTs
  - [x] Delegation and Staking information
- [x] Transactions
  - [x] Paged fetching based on timestamp (allows continuing searches)
  - [x] Processing/interpretation (decodes data and SC results for easy usage)
  - [x] Execute new transaction
- [x] Core data
  - [x] Network config/status
  - [x] Nodes (validators) data
  - [x] Staking Providers
    - [ ] Get delegators
  - [x] ESDTs (including MetaESDTs)
  - [x] NFTs
    - [ ] Fetch assets
  - [x] SFTs
- [x] MemCache
  - [x] In process
  - [x] Redis (optional)
  - [ ] Cache more resources
- [ ] Better input data validation
- [ ] Official deployment using API keys

## License
[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)