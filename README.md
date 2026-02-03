# l9g-accountinfo

## Project Overview

This project is a Java-based web application built with Spring Boot. It's designed to display OAuth2/OIDC token information. The application uses Maven for dependency management and can be run as a standalone JAR file or as a Docker container. It uses Thymeleaf for server-side rendering of HTML templates and includes features like WebSocket communication and SSL support.

## Building and Running

### Building from Source

To build the project from source, you'll need to have Java 21 and Maven installed. Then, you can run the following command from the project's root directory:

```bash
mvn clean package
```

This will create a JAR file in the `target` directory.

### Running the Application

#### Running as a JAR file

To run the application as a JAR file, you can use the following command:

```bash
java -jar target/l9g-accountinfo.jar
```

You can customize the application's configuration by creating a `config.yaml` file in the same directory as the JAR file. You can use the `config.yaml.sample` file as a template.

#### Running with Docker

To run the application with Docker, you can use the `docker-compose.yaml` file located in the `docker` directory. First, you'll need to build the Docker image. You can use the `BUILD_IMAGE.sh` script in the `docker` directory to do this:

```bash
cd docker
./BUILD_IMAGE.sh
```

Then, you can run the application using Docker Compose:

```bash
docker-compose up -d
```

This will start the application in the background. You can view the logs using the following command:

```bash
docker-compose logs -f
```