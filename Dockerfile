FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/api-blitz.jar /api-blitz/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/api-blitz/app.jar"]
