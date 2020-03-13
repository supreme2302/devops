FROM openjdk:8u171-jre-stretch
VOLUME /tmp
ARG DEPENDENCY=build/dependency
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app
EXPOSE 8080 8081
ENTRYPOINT ["java","-cp","app:app/lib/*","ru.supreme.devops.ConsumerApplication"]