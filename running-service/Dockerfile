FROM eclipse-temurin:21-jre AS builder

WORKDIR /builder

COPY build/libs/*.jar application.jar

RUN java -Djarmode=tools -jar application.jar \
    extract --layers --destination extracted

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring spring \
    && mkdir -p /app/logs \
    && chown spring:spring /app/logs

COPY --from=builder --chown=spring:spring /builder/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /builder/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /builder/extracted/application/ ./

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
