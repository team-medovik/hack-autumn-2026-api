FROM bellsoft/liberica-runtime-container:jre-25-slim-glibc
RUN mkdir -p /config
COPY ./build/libs/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
