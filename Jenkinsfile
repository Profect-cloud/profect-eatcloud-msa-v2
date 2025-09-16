pipeline {
  agent none
  options { timestamps() }
  environment {
    AWS_REGION = 'ap-northeast-2'
    SVC = 'auth-service' // 테스트 대상
  }
  stages {
    stage('Checkout'){ agent any; steps { checkout scm } }

    stage('Build & Push with Kaniko'){
      agent {
        kubernetes {
          defaultContainer 'kaniko'
          yaml """
apiVersion: v1
kind: Pod
metadata:
  namespace: jenkins
spec:
  serviceAccountName: kaniko
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:latest
    args: ['sleep','infinity']
    volumeMounts:
    - name: kaniko-cache
      mountPath: /kaniko/.cache
  volumes:
  - name: kaniko-cache
    emptyDir: {}
"""
        }
      }
      steps {
        script {
          def accountId = sh(returnStdout:true, script: "aws sts get-caller-identity --query Account --output text").trim()
          def registry  = "${accountId}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
          def repo      = "${registry}/eatcloud-${env.SVC}"
          def tag       = env.BUILD_NUMBER

          sh """
            aws ecr describe-repositories --repository-name eatcloud-${SVC} --region ${AWS_REGION} >/dev/null 2>&1 || \
              aws ecr create-repository --repository-name eatcloud-${SVC} --region ${AWS_REGION}

            /kaniko/executor \
              --destination=${repo}:${tag} \
              --context=${SVC} \
              --dockerfile=${SVC}/Dockerfile \
              --cache=true --cache-ttl=24h --snapshotMode=redo --use-new-run
          """
          echo "Pushed: ${repo}:${tag}"
        }
      }
    }
  }
}
