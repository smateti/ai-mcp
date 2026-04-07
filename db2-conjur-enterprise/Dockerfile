FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh

COPY --chown=1001:0 target/dependency/db2jcc.jar /config/lib/db2/db2jcc.jar
COPY --chown=1001:0 target/db2-conjur-enterprise.war /config/apps/

ENV CONJUR_APPLIANCE_URL=http://conjur:80
ENV CONJUR_ACCOUNT=myConjurAccount
ENV CONJUR_AUTHN_LOGIN=host/myapp/liberty-app
ENV CONJUR_AUTHN_API_KEY=not-set
ENV DB_HOST=appdb
ENV DB_PORT=50000
ENV DB_NAME=empdb
ENV DB_USERNAME=db2inst1
ENV DB_PASSWORD=change-me

EXPOSE 9080 9443
RUN configure.sh
