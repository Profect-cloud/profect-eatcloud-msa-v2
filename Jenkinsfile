// Jenkinsfile (root)
pipeline {
  agent any
  options { timestamps() }
  environment {
    AWS_REGION    = 'ap-northeast-2'
    ECR_REGISTRY  = ''          // 아래에서 aws sts로 채움
    BASE_REF      = 'origin/main'  // 변경 감지 기준 브랜치(적절히 조정)
  }

  stages {
    stage('Checkout'){ steps { checkout scm } }

    stage('Detect changed services'){
      steps {
        script {
          sh "git fetch --all --prune || true"
          def changed = sh(returnStdout:true,
            script: "git diff --name-only ${env.BASE_REF}...HEAD || git ls-files").trim().split('\\r?\\n')
          // 최상위 디렉터리 중 Dockerfile이 있는 것만 서비스로 간주 (deploy만 변경이면 스킵)
          def svcs = [] as Set
          def onlyDeploy = true
          changed.each { p ->
            if (!p.startsWith('deploy/')) onlyDeploy = false
            def m = (p =~ /^([^\\/]+)\\//)
            if (m.find()) {
              def top = m.group(1)
              if (fileExists("${top}/Dockerfile")) svcs << top
            }
          }
          if (onlyDeploy) { echo "Only deploy/ changed → skip build."; currentBuild.result='SUCCESS'; return }
          if (svcs.isEmpty()) { echo "No service changes."; currentBuild.result='SUCCESS'; return }
          env.SERVICES = svcs.join(' ')
          echo "Changed services: ${env.SERVICES}"
        }
      }
    }

    stage('Login ECR'){
      when { expression { return (env.SERVICES?.trim()) } }
      steps {
        script {
          def accountId = sh(returnStdout:true, script: "aws sts get-caller-identity --query Account --output text").trim()
          env.ECR_REGISTRY = "${accountId}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
        }
        sh """
          aws ecr get-login-password --region ${AWS_REGION} \
          | docker login --username AWS --password-stdin ${ECR_REGISTRY}
        """
      }
    }

    stage('Build & Push images'){
      when { expression { return (env.SERVICES?.trim()) } }
      steps {
        script {
          env.SERVICES.split(' ').each { svc ->
            // ECR 리포 규칙: eatcloud-<서비스디렉터리명>
            def repo = "${env.ECR_REGISTRY}/eatcloud-${svc}"
            def tag  = "${env.BUILD_NUMBER}"
            dir(svc) {
              sh "./gradlew clean test || true"
              sh """
                aws ecr describe-repositories --repository-name eatcloud-${svc} --region ${AWS_REGION} >/dev/null 2>&1 || \
                  aws ecr create-repository --repository-name eatcloud-${svc} --region ${AWS_REGION}
                docker build -t ${repo}:${tag} .
                docker push ${repo}:${tag}
              """
            }
          }
        }
      }
    }

    stage('Bump all-apps.yaml (image.tag)'){
      when { expression { return (env.SERVICES?.trim()) } }
      steps {
        // all-apps.yaml에서 해당 Application의 image.tag만 갱신
        sh "which yq || brew install yq" // mac 에이전트면 임시 설치
        script {
          env.SERVICES.split(' ').each { svc ->
            sh """
              yq -i '
                (select(.kind=="Application" and .metadata.name=="${svc}")
                 .spec.source.helm.parameters[]
                 | select(.name=="image.tag")).value = "${BUILD_NUMBER}"
              ' deploy/apps/all-apps.yaml
            """
          }
        }
        sh """
          git config user.email "ci@bot"
          git config user.name "ci-bot"
          git add deploy/apps/all-apps.yaml
          git commit -m "[skip ci] bump: ${SERVICES} -> ${BUILD_NUMBER}" || true
          git push origin ${env.BRANCH_NAME}
        """
      }
    }
  }
}
