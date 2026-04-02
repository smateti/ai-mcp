FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh

COPY --chown=1001:0 target/dependency/db2jcc.jar /config/lib/db2/db2jcc.jar
COPY --chown=1001:0 target/employee-app.war /config/apps/

ENV CONJUR_APPLIANCE_URL=http://conjur-server.conjur-system.svc.cluster.local
ENV CONJUR_ACCOUNT=nimbusConjurAccount
ENV CONJUR_AUTHN_LOGIN=host/apps/liberty-app
ENV CONJUR_AUTHN_API_KEY=not-set
ENV DB_HOST=db2.databases.svc.cluster.local
ENV DB_PORT=50000
ENV DB_NAME=empdb
# DB_USERNAME and DB_PASSWORD are fetched from Conjur at runtime
ENV DB_CONJUR_UID_KEY=db/empdb-uid
ENV DB_CONJUR_PWD_KEY=db/empdb-pwd

EXPOSE 9080 9443
RUN configure.sh
