FROM clojure
ENV APP_JAR pav-conf.jar

WORKDIR /app
COPY . /app

RUN cp target/${APP_JAR} ${APP_JAR}
CMD java -jar ${APP_JAR}
