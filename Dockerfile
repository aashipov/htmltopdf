FROM aashipov/docker:builder AS builder
USER root
WORKDIR /dummy/
COPY --chown=dummy:dummy ./ ./
RUN chmod +x /dummy/entrypoint.bash
USER dummy
RUN go build

FROM aashipov/docker:wknch
USER root
EXPOSE 8080
COPY --from=builder --chown=dummy:dummy /dummy/htmltopdf /dummy/
COPY --from=builder --chown=dummy:dummy /dummy/entrypoint.bash /dummy/
WORKDIR /dummy/
USER dummy
ENTRYPOINT [ "/dummy/entrypoint.bash" ]
