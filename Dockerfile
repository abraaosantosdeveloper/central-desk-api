# Imagem de produção do back-end (Railway). Duas etapas: compila com o JDK e roda só com o JRE.
# Os testes não rodam aqui: eles usam Testcontainers (precisam de Docker) e rodam antes, com .\mvnw test.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
# UTF-8 para ler variáveis de ambiente com acento (ex.: ADMIN_NAME=Abraão)
ENV LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
RUN useradd --system --uid 10001 centraldesk
COPY --from=build /app/app.jar app.jar
USER centraldesk
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
