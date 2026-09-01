pipeline {
    agent any

    stages {
        stage('Deploy') {
            steps {
                sh '''
                cd Infra
                docker-compose down
                docker-compose up -d --build
                '''
            }
        }
    }

    post {
        success {
            mattermostSend(
                color: 'good',
                message: "✅ 배포 성공\n- Job: ${env.JOB_NAME}\n- Build: #${env.BUILD_NUMBER}\n- 트리거: ${env.gitlabActionType ?: 'PUSH'}\n- Branch: ${env.gitlabTargetBranch ?: env.gitlabBranch}\n- URL: ${env.BUILD_URL}",
                endpoint: env.MATTERMOST_WEBHOOK_URL,
                channel: 'PUSH'
            )
        }
        failure {
            mattermostSend(
                color: 'danger',
                message: "❌ 배포 실패\n- Job: ${env.JOB_NAME}\n- Build: #${env.BUILD_NUMBER}\n- 트리거: ${env.gitlabActionType ?: 'PUSH'}\n- Branch: ${env.gitlabTargetBranch ?: env.gitlabBranch}\n- URL: ${env.BUILD_URL}",
                endpoint: env.MATTERMOST_WEBHOOK_URL,
                channel: 'PUSH'
            )
        }
    }
}
