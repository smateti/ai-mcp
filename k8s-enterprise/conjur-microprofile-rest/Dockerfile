FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh

COPY --chown=1001:0 target/conjur-microprofile-rest.war /config/apps/

ENV CONJUR_APPLIANCE_URL=http://conjur-server.conjur-system.svc.cluster.local
ENV CONJUR_ACCOUNT=nimbusConjurAccount
ENV CONJUR_AUTHN_LOGIN=admin
ENV CONJUR_ADMIN_API_KEY=not-set

USER 0
RUN mkdir -p /data && chown 1001:0 /data
USER 1001

EXPOSE 9080
RUN configure.sh
