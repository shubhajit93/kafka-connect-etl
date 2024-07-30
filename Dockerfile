#########################################################
# Maven builder image to build the custom connectors
#########################################################
FROM maven:3.6.3-openjdk-11 as build-stage
COPY . /app
WORKDIR /app
RUN mvn -B clean package --file pom.xml

#########################################################
# Custom Kafka Connect Docker image
#########################################################

FROM confluentinc/cp-kafka-connect:7.0.1
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:latest
RUN mkdir /usr/share/java/etl
COPY --from=build-stage /app/target/connect-etl-*.jar /usr/share/java/etl

# Add the command to be executed when the container starts
CMD ["bash", "-c", "/etc/confluent/docker/run"]