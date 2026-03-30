FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh

COPY --chown=1001:0 target/propsetapp.war /config/apps/
COPY --chown=1001:0 config/datasources.yaml /config/datasources.yaml

ENV PROPSET_CONFIG_PATH=/config/datasources.yaml

EXPOSE 9080
RUN configure.sh
