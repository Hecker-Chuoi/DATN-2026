// Job Multibranch DATN/ci/datn (docs/cicd/03 §12.3, 04 §11)
// PR / nhánh : test backend, lint+build frontend, helm lint + kubeconform
// main       : + build/quét/push 6 image (tag = git SHA) + helm upgrade staging
// tag vX.Y.Z : KHÔNG build lại; gắn tag phiên bản cho image đã có rồi kích hoạt job duyệt deploy prod
pipeline {
  agent none
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    disableConcurrentBuilds()
    timeout(time: 90, unit: 'MINUTES')
  }
  environment {
    REGISTRY    = 'ghcr.io/hecker-chuoi'
    HELM_DRIVER = 'configmap'
    MAVEN_OPTS  = '-Xmx768m'
    STAGING_URL = 'https://staging.example.com'
  }
  stages {
    stage('CI') {
      when { not { buildingTag() } }
      agent any
      tools {
        jdk 'jdk-21'
        maven 'maven-3.9'
        nodejs 'node-22'
      }
      stages {
        stage('Backend: build & unit test') {
          steps {
            // IdentityServiceApplicationTests (@SpringBootTest) cần Postgres thật ở localhost:5433, CI không có:
            // loại riêng test đó, mọi test unit khác vẫn chạy.
            dir('backend') {
              sh 'mvn -B -ntp verify "-Dtest=!IdentityServiceApplicationTests" -Dsurefire.failIfNoSpecifiedTests=false'
            }
          }
          post {
            always { junit allowEmptyResults: true, testResults: 'backend/**/target/surefire-reports/*.xml' }
          }
        }
        stage('Frontend: lint & build') {
          steps {
            dir('frontend') {
              sh 'npm ci --no-audit --no-fund'
              // Lint CHƯA chặn (còn ~190 lỗi có từ trước): lỗi thì stage UNSTABLE, build vẫn tiếp tục
              catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') { sh 'npm run lint' }
              sh 'npm run build'
            }
          }
        }
        stage('Helm lint & kubeconform') {
          steps {
            sh '''
              set -eu
              for e in staging prod; do
                for c in datn-platform datn-app; do
                  helm lint infrastructure/helm/$c -f infrastructure/helm/$c/values-$e.yaml
                  helm template $c infrastructure/helm/$c -n datn-$e -f infrastructure/helm/$c/values-$e.yaml \
                    | kubeconform -strict -summary -ignore-missing-schemas
                done
              done
            '''
          }
        }
        stage('Build, quét & push image') {
          when { branch 'main' }
          steps {
            withCredentials([usernamePassword(credentialsId: 'ghcr-push', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
              sh '''
                echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
                bash infrastructure/scripts/build-images.sh "$GIT_COMMIT"
              '''
            }
          }
          post { always { sh 'docker logout ghcr.io || true' } }
        }
        stage('Deploy staging') {
          when { branch 'main' }
          options { lock('datn-deploy-staging') }
          steps {
            withCredentials([file(credentialsId: 'kubeconfig-staging', variable: 'KUBECONFIG')]) {
              sh 'bash infrastructure/scripts/deploy.sh staging datn-staging "$GIT_COMMIT" "$STAGING_URL"'
            }
          }
        }
      }
      post {
        always { cleanWs(notFailBuild: true) }
      }
    }

    stage('Release') {
      when { tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP' }
      stages {
        stage('Gắn tag phiên bản cho image') {
          agent any
          steps {
            script { env.RELEASE_SHA = env.GIT_COMMIT }
            withCredentials([usernamePassword(credentialsId: 'ghcr-push', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
              sh '''
                set -eu
                echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
                for s in api-gateway identity-service work-service workspace-service notification-service frontend; do
                  src="$REGISTRY/datn-$s:$GIT_COMMIT"
                  docker buildx imagetools inspect "$src" > /dev/null \
                    || { echo "Chưa có $src. Commit được tag phải qua pipeline main trước."; exit 1; }
                  docker buildx imagetools create -t "$REGISTRY/datn-$s:$TAG_NAME" "$src"
                done
              '''
            }
          }
          post {
            always {
              sh 'docker logout ghcr.io || true'
              cleanWs(notFailBuild: true)
            }
          }
        }
        stage('Kích hoạt job duyệt & deploy prod') {
          // Không có agent: không giữ executor
          steps {
            build job: '/DATN/release/datn-deploy', wait: false, parameters: [
              string(name: 'TARGET', value: 'production'),
              string(name: 'ACTION', value: 'deploy'),
              string(name: 'IMAGE_TAG', value: env.RELEASE_SHA),
              string(name: 'REVISION', value: ''),
              string(name: 'VERSION', value: env.TAG_NAME)
            ]
          }
        }
      }
    }
  }
}
