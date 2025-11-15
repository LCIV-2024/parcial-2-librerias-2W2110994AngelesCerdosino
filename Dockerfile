FROM eclipse-temurin:17-jdk
WORKDIR /app

RUN apt-get update && apt-get install -y maven

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

EXPOSE 8080
CMD ["java", "-jar", "target/libreria-0.0.1-SNAPSHOT.jar"]