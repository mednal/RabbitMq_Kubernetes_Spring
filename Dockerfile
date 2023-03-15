FROM openjdk:8-alpine
ENV RABBITMQ_HOST=172.17.0.3 \
    RABBITMQ_PORT=5672 \
    RABBITMQ_USERNAME=guest \
    RABBITMQ_PASSWORD=guest \
    QUEUE_NAME=projqueue
RUN apk update && \
        apk add --no-cache bash rabbitmq-c && \
        rm -rf /var/cache/apk/*

ARG JAR_FILE=target/*.jar
COPY pom.xml .
COPY ./target/Proj-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java","-jar","app.jar"]

