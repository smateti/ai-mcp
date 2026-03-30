FROM alpine:3.19

RUN apk add --no-cache bash curl python3

COPY fetch-secrets.sh /usr/local/bin/fetch-secrets.sh
RUN chmod +x /usr/local/bin/fetch-secrets.sh

ENTRYPOINT ["/usr/local/bin/fetch-secrets.sh"]
