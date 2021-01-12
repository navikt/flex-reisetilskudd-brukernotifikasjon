echo "Bygger flex-reisetilskudd-brukernotifikasjon latest"

./gradlew bootJar

docker build . -t flex-reisetilskudd-brukernotifikasjon:latest
