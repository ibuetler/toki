FROM alpine:3.22

ARG PB_VERSION=0.40.5
ARG TARGETARCH

RUN apk add --no-cache \
    ca-certificates \
    unzip \
    wget

WORKDIR /pb

RUN case "${TARGETARCH}" in \
      amd64|arm64) ;; \
      *) echo "Unsupported architecture: ${TARGETARCH}" && exit 1 ;; \
    esac && \
    wget -q \
      "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_${TARGETARCH}.zip" \
      -O /tmp/pocketbase.zip && \
    unzip /tmp/pocketbase.zip -d /pb && \
    rm /tmp/pocketbase.zip && \
    chmod +x /pb/pocketbase

EXPOSE 8090

CMD ["/pb/pocketbase", "serve", "--http=0.0.0.0:8090"]
