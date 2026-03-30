FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh

COPY --chown=1001:0 target/conjur-microprofile-web.war /config/apps/

EXPOSE 9080
RUN configure.sh
