FROM centos:7 AS base
ARG WKHTMLTOX_VERSION=0.12.6-1
ARG WKHTMLTOX_RPM=wkhtmltox-$WKHTMLTOX_VERSION.centos7.x86_64.rpm
ADD https://github.com/wkhtmltopdf/packaging/releases/download/$WKHTMLTOX_VERSION/$WKHTMLTOX_RPM /tmp/
# An unprivileged user without sudo, no need in chromium sandboxing or seccomp
RUN groupadd dummy ; useradd -d /dummy/ -m -g dummy dummy ; \
yum install -y epel-release ; \
# we only need chromium-headless, but there are missing files only available in chromium package https://github.com/elastic/kibana/issues/28408
yum install -y chromium-headless fontconfig /tmp/$WKHTMLTOX_RPM ; rm -rf /tmp/$WKHTMLTOX_RPM ; \
yum clean all ; ln -s /usr/lib64/chromium-browser/headless_shell /usr/bin/chromium

FROM base AS buildbed
USER root
RUN rpm --import https://mirror.go-repo.io/centos/RPM-GPG-KEY-GO-REPO ; \
curl -s https://mirror.go-repo.io/centos/go-repo.repo | tee /etc/yum.repos.d/go-repo.repo
RUN yum install -y chromium golang

FROM buildbed AS builder
USER root
WORKDIR /dummy/
COPY --chown=dummy:dummy ./ ./
RUN chmod +x /dummy/entrypoint.bash
USER dummy
RUN go build

FROM base AS final
USER root
EXPOSE 8080
COPY --from=builder /usr/lib64/chromium-browser/swiftshader/ /usr/lib64/chromium-browser/swiftshader/
COPY --from=builder --chown=dummy:dummy /dummy/htmltopdf /dummy/
COPY --from=builder --chown=dummy:dummy /dummy/entrypoint.bash /dummy/
WORKDIR /dummy/
USER dummy
ENTRYPOINT [ "/dummy/entrypoint.bash" ]
HEALTHCHECK CMD curl -f http://localhost:8080/health || exit 1
