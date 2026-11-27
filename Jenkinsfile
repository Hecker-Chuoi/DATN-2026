// CI/CD DATN-2026: GitHub -> Jenkins -> Docker Hub -> SSH sang server ứng dụng -> docker compose.
// Hướng dẫn: docs/cicd/cac_buoc/06_NOI_JENKINS_VOI_SERVER_UNG_DUNG.md. Tên tool / credential khớp docs/cicd/cac_buoc/01 §8–9.
//
// Cần sửa 1 chỗ trong `environment` (hoặc đặt ở Manage Jenkins > System > Global properties > Environment variables):
//   APP_SERVER = IP PRIVATE của server ứng dụng (cùng VPC với Jenkins), ví dụ 172.31.40.12
// REGISTRY không cần khai: build-push.sh đọc từ infrastructure/prod/public.env.
pipeline {
  agent any
  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 90, unit: 'MINUTES')
  }
  tools { jdk 'jdk-21'; maven 'maven-3.9'; nodejs 'node-22' }
  environment {
    APP_SERVER = '172.31.0.0'          // ĐỔI thành IP private của server ứng dụng
  }
  stages {
    stage('Init') {
      steps { script { env.TAG = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim() } }
    }
    stage('Test backend') {
      steps { dir('backend') { sh 'mvn -B -ntp verify' } }
      post  { always { junit allowEmptyResults: true, testResults: 'backend/**/target/surefire-reports/*.xml' } }
    }
    stage('Frontend lint + build') {
      steps { dir('frontend') { sh 'npm ci && npm run lint && npm run build' } }
    }
    stage('Build & push images') {
      when { branch 'main' }
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
          sh '''
            echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin
            bash infrastructure/ci/build-push.sh "$TAG"
          '''
        }
      }
      post { always { sh 'docker logout || true' } }
    }
    stage('Deploy') {
      when { branch 'main' }
      steps {
        // Gói gồm compose + cấu hình + realm export; deploy.sh trên server giải nén, kéo image, `up -d --wait`, smoke test, tự rollback khi lỗi
        sshagent(['app-server-ssh']) {
          sh 'tar cz -C infrastructure docker-compose.prod.yml prod keycloak | ssh -o BatchMode=yes deployer@$APP_SERVER "deploy $TAG"'
        }
      }
    }
  }
  post { always { cleanWs() } }
}
