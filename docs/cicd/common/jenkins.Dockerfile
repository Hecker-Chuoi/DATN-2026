# =============================================================================
# Image Jenkins tuỳ biến cho DATN-2026 (dùng cho CẢ Hướng 1 và Hướng 2)
#   - Jenkins LTS + JDK 21 (có sẵn trong image gốc → build được Java 21 của backend)
#   - Maven 3.9 + Node.js 22 (chạy test backend, lint/build frontend ngay trong Jenkins)
#   - Docker CLI + Compose + Buildx: KHÔNG có Docker daemon bên trong; Jenkins điều khiển
#     daemon của máy EC2 thông qua /var/run/docker.sock được mount vào
#   - kubectl: chỉ cài khi build với --build-arg INSTALL_KUBECTL=true (Hướng 2)
# Đặt trên server tại: /opt/datn/platform/jenkins.Dockerfile
# =============================================================================
FROM jenkins/jenkins:lts-jdk21

USER root

ARG MAVEN_VERSION=3.9.9
ARG NODE_MAJOR=22
ARG INSTALL_KUBECTL=false

# 1) Docker CLI + compose plugin + buildx (từ repo chính thức của Docker)
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg git \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && chmod a+r /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin docker-compose-plugin \
 && rm -rf /var/lib/apt/lists/*

# 2) Maven (bản chính thức, không dùng bản cũ của apt)
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
      | tar -xz -C /opt \
 && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

# 3) Node.js 22 (Vite 8 cần Node ^20.19 hoặc >=22.12)
RUN curl -fsSL https://deb.nodesource.com/setup_${NODE_MAJOR}.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && rm -rf /var/lib/apt/lists/*

# 4) kubectl (chỉ Hướng 2 – Kubernetes)
RUN if [ "$INSTALL_KUBECTL" = "true" ]; then \
      curl -fsSLo /usr/local/bin/kubectl \
        "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/$(dpkg --print-architecture)/kubectl" \
      && chmod +x /usr/local/bin/kubectl ; \
    fi

USER jenkins

# 5) Plugin cần thiết (cài sẵn để không phải bấm tay trong UI)
#   github-branch-source : Multibranch Pipeline + nhận webhook GitHub
#   workflow-aggregator  : Pipeline (Declarative)
#   pipeline-graph-view  : xem sơ đồ stage trực quan
#   credentials-binding  : withCredentials(...) để dùng secret file / kubeconfig
#   junit                : hiển thị kết quả unit test
#   timestamper, ws-cleanup, email-ext : log có giờ, dọn workspace, gửi mail kết quả
RUN jenkins-plugin-cli --plugins \
      git github-branch-source workflow-aggregator pipeline-graph-view \
      credentials-binding junit timestamper ws-cleanup email-ext
