FROM openjdk:11-jdk
EXPOSE 8080:8080
RUN mkdir /app
COPY ./build/install/com.beeftechlabs.elrondapi/ /app/
COPY ./config.yaml /app/bin/
WORKDIR /app/bin
CMD ["./com.beeftechlabs.elrondapi"]
