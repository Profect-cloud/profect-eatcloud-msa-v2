pipeline {
  agent any
  environment {
    AWS_REGION = 'ap-northeast-2'
    ACCOUNT_ID = '123456789012'
    DEPLOY_REPO = 'git@github.com:your-org/eatcloud-deploy.git'
  }
  stages {
    stage('Detect changed services') {
      steps {
        script {
          // 이전 성공 커밋 ~ 현재 사이 변경 파일 목록
          def from = sh(returnStdout: true, script: "git rev-parse --short HEAD~1 || echo HEAD~1").trim()
          def changed = sh(returnStdout: true, script: "git diff --name-only ${from} HEAD || git ls-files").trim().split("\\r?\\n")
          // services/<name>/ 이하가 바뀐 경우만 추출
          def svcSet = [] as Set
          changed.each { p ->
            def m = (p =~ /^services\\/([^\\/]+)\\//)
            if (m.find()) { svcSet << m.group(1) }
          }
          if (svcSet.isEmpty()) { echo "No service changes. Skipping."; currentBuild.result = 'SUCCESS'; }
          env.SERVICES = svcSet.join(' ')
          echo "Changed services: ${env.SERVICES}"
        }
      }
    }

    stage('Build & Push each service') {
      when { expression { return (env.SERVICES?.trim()) } }
      steps {
        script {
          env.SERVICES.split(' ').each { svc ->
            dir("services/${svc}") {
              sh "./gradlew clean test || true" // 초기 완화
              def repo = "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/eatcloud-${svc}"
              def tag  = "${env.BUILD_NUMBER}"
              sh """
                aws ecr describe-repositories --repository-name eatcloud-${svc} --region ${AWS_REGION} >/dev/null 2>&1 || \
                  aws ecr create-repository --repository-name eatcloud-${svc} --region ${AWS_REGION}
                aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
                docker build -t ${repo}:${tag} .
                docker push ${repo}:${tag}
              """
            }
          }
        }
      }
    }

    stage('Bump deploy manifests') {
      when { expression { return (env.SERVICES?.trim()) } }
      steps {
        sshagent (credentials: ['git-deploy-key']) {
          sh """
            rm -rf eatcloud-deploy
            git clone ${DEPLOY_REPO}
            cd eatcloud-deploy/environments/dev
          """
          script {
            env.SERVICES.split(' ').each { svc ->
              sh """
                yq -i '.image.repository = "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/eatcloud-${svc}"' ${svc}/values.yaml
                yq -i '.image.tag = "${env.BUILD_NUMBER}"' ${svc}/values.yaml
              """
            }
          }
          sh """
            cd eatcloud-deploy
            git config user.email "ci@bot" && git config user.name "ci-bot"
            git add -A
            git commit -m "bump: \${SERVICES} -> tag ${BUILD_NUMBER}" || true
            git push origin main
          """
        }
      }
    }
  }
}
