FROM aashipov/htmltopdf:buildbed AS builder
USER root
WORKDIR /dummy/
COPY --chown=dummy:dummy ./ ./
RUN chmod +x /dummy/entrypoint.bash
USER dummy
RUN go build

FROM aashipov/htmltopdf:base
USER root
EXPOSE 8080
COPY --from=builder /usr/lib64/chromium-browser/swiftshader/ /usr/lib64/chromium-browser/swiftshader/
COPY --from=builder --chown=dummy:dummy /dummy/htmltopdf /dummy/
COPY --from=builder --chown=dummy:dummy /dummy/entrypoint.bash /dummy/
WORKDIR /dummy/
USER dummy
ENTRYPOINT [ "/dummy/entrypoint.bash" ]
HEALTHCHECK CMD curl -f http://localhost:8080/health || exit 1
