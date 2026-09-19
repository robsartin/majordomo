FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src src
COPY config config
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre

# OCR for scanned documents and photographed receipts (#341, ADR-0027).
#
# tesseract-ocr-eng is named explicitly although Debian's tesseract-ocr already
# Depends on it. Without language data tesseract recognises nothing and still
# exits zero, so the failure would be silent — worth stating as our own
# requirement rather than inheriting from someone else's dependency list.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=9090"]
